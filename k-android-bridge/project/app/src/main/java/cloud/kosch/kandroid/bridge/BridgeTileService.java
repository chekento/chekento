package cloud.kosch.kandroid.bridge;
import android.content.*;import android.service.quicksettings.TileService;
public class BridgeTileService extends TileService { @Override public void onClick(){super.onClick();Intent i=new Intent(this,CaptureActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivityAndCollapse(i);} }
