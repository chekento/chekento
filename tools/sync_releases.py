"""Refresh real APK download totals and newest public APK links (including previews)."""
import argparse
import datetime as dt
import html
import json
import os
from pathlib import Path
import re
import urllib.request


def get(url):
    headers = {'Accept': 'application/vnd.github+json', 'User-Agent': 'kosch-release-sync'}
    if os.environ.get('GH_TOKEN'):
        headers['Authorization'] = 'Bearer ' + os.environ['GH_TOKEN']
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=45) as response:
        return json.load(response)


def releases(repo):
    result = []
    page = 1
    while True:
        batch = get(f'https://api.github.com/repos/chekento/{repo}/releases?per_page=100&page={page}')
        result.extend(batch)
        if len(batch) < 100:
            return result
        page += 1


def summarize(rows):
    public = [r for r in rows if not r['draft']]
    pairs = [(r, a) for r in public for a in r['assets']
             if a['name'].lower().endswith('.apk') and a.get('state', 'uploaded') == 'uploaded']
    if not pairs:
        raise ValueError('No published APK asset found; preserve previous links and counts')
    # Asset timestamps also support rolling releases published long ago.
    release, apk = max(pairs, key=lambda pair: (
        pair[1].get('created_at', pair[0]['published_at']),
        pair[0]['published_at'], pair[1]['name']))
    return dict(tag=release['tag_name'], prerelease=release['prerelease'],
                url=apk['browser_download_url'], release=release['html_url'],
                total=sum(a['download_count'] for _, a in pairs),
                latest_downloads=apk['download_count'], asset=apk['name'])


def block(text, marker, content):
    pattern = rf'<!-- {marker}:START -->.*?<!-- {marker}:END -->'
    result, count = re.subn(pattern, lambda _: f'<!-- {marker}:START -->\n{content}\n<!-- {marker}:END -->', text, flags=re.S)
    if count != 1:
        raise ValueError(f'Expected one {marker} block, found {count}')
    return result


def badge(count):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="172" height="26" role="img" aria-label="APK downloads: {count}">
<title>APK downloads across all public releases: {count}</title><rect width="172" height="26" rx="6" fill="#173947"/>
<text x="10" y="17" fill="#dce9ec" font-family="Arial,sans-serif" font-size="12">APK downloads</text>
<text x="160" y="17" text-anchor="end" fill="#9be0cb" font-family="Arial,sans-serif" font-size="12" font-weight="700">{count}</text></svg>\n'''


def sync(snapshot=None, ski=False):
    def fetch(repo):
        return summarize(snapshot[repo] if snapshot is not None else releases(repo))
    if ski:
        release = fetch('SkiZones')
        text = Path('README.md').read_text()
        content = f'''[![Download APK · neueste veröffentlichte Android-Preview](docs/images/download-apk.svg)]({release['url']})

**Aktueller Download: {release['tag']} · Android ARM64 · Preview**

[Release & Installation]({release['release']}) · [Alle Versionen](https://github.com/chekento/SkiZones/releases) · [Download-Statistik](docs/downloads.json)

![APK-Downloads über alle öffentlichen Releases](docs/images/apk-downloads.svg)

*Nur APK-Dateien werden gezählt, einschließlich Previews. Prüfsummen und Metadaten sind ausgeschlossen. Downloads sind keine eindeutigen Installationen. Der Download-Link wird nach dem Build und zusätzlich stündlich aktualisiert; GitHub-Bildcaches können verzögern.*'''
        Path('README.md').write_text(block(text, 'APK-DOWNLOAD', content))
        Path('docs/images').mkdir(parents=True, exist_ok=True)
        Path('docs/images/apk-downloads.svg').write_text(badge(release['total']))
        Path('docs/downloads.json').write_text(json.dumps(release, indent=2)+'\n')
        print(f"SkiZones: {release['tag']}, {release['total']} APK downloads")
        return

    apps = json.loads(Path('tools/apps.json').read_text())
    # Complete every read before mutating generated files. API errors must not become zeroes.
    metadata = {app['id']: fetch(app['repo']) for app in apps}
    text = Path('README.md').read_text()
    total = sum(value['total'] for value in metadata.values())
    lines = [f'**{total} APK downloads** across the 11 projects.', '',
             '| Project | Explore | Android APK | APK downloads |',
             '|---|---|---|---:|']
    for app in apps:
        r = metadata[app['id']]
        label = r['tag'] + (' · Preview' if r['prerelease'] else '')
        lines.append(f"| **[{app['name']}](https://github.com/chekento/{app['repo']})** | {app['description']} | [↓ {label}]({r['url']}) | ![{r['total']} APK downloads](assets/downloads/{app['id']}.svg) |")
    text = block(text, 'APP-CATALOG', '\n'.join(lines))
    ski_release = metadata['skizones']
    text = block(text, 'SKIZONES-DOWNLOAD', f'''[![Download SkiZones APK](https://raw.githubusercontent.com/chekento/SkiZones/main/docs/images/download-apk.svg)]({ski_release['url']})

[{ski_release['tag']} · Android Preview]({ski_release['release']}) · [Game & documentation](https://github.com/chekento/SkiZones) · ![{ski_release['total']} APK downloads](assets/downloads/skizones.svg)''')
    store = Path('docs/store.html').read_text()
    store, count = re.subn(r'// RELEASE-DATA:START.*?// RELEASE-DATA:END',
        lambda _: '// RELEASE-DATA:START\nconst releaseData = '+json.dumps(metadata, ensure_ascii=False)+';\n// RELEASE-DATA:END', store, flags=re.S)
    if count != 1:
        raise ValueError('Expected one store release-data block')
    for app in apps:
        p = Path('assets/downloads') / (app['id'] + '.svg')
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(badge(metadata[app['id']]['total']))
        card = Path('assets/app-gallery') / (app['id']+'.svg')
        if card.exists():
            card.write_text(re.sub(r'(<text id="apk-download-count"[^>]*>↓ )\d+( APK downloads</text>)',
                lambda m: m[1]+str(metadata[app['id']]['total'])+m[2], card.read_text()))
    Path('README.md').write_text(text)
    Path('docs/store.html').write_text(store)
    Path('docs/downloads.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2)+'\n')
    print(f'{len(apps)} projects; {total} APK downloads')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--snapshot', type=Path, help='Use a saved API response for deterministic validation')
    parser.add_argument('--ski', action='store_true')
    args = parser.parse_args()
    sync(json.loads(args.snapshot.read_text()) if args.snapshot else None, args.ski)
