package com.zcs.nbbiometricdemo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Process;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.nextbiometrics.devices.NBDevice;
import com.nextbiometrics.devices.NBDeviceScanFormatInfo;
import com.nextbiometrics.devices.NBDeviceScanResult;
import com.nextbiometrics.devices.NBDeviceScanStatus;
import com.nextbiometrics.devices.event.NBDeviceScanPreviewEvent;
import com.nextbiometrics.devices.event.NBDeviceScanPreviewListener;

import java.nio.IntBuffer;

/*
 *  The Capture Activity is used to allow the testing of device for displaying a scanned finger
 *  in two different versions - either the snapshot of a fingerprint or a real fingerprint as defined
 *  by NB
 * */
public class CaptureActivity extends AppCompatActivity implements OnClickListener, ConnectionManager.ChangesListener {
  
  private TextView log;
  private ImageView fingerImage;
  private Button scanSnapshotBtn;
  private Button scanBtn;
  private Button statusBtn;
  private ScanTask scanTask;
  
  private ConnectionManager connection;
  
  /* ActionBar related function */
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }
  
  /* ActionBar related functions */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    
    NBDevice device = connection.GetDevice();
    Intent intent;
    
    if (item.getItemId() == R.id.miEnrollIdentify) {
      intent = new Intent(this, EVActivity.class);
      //intent.putExtra("deviceId", device.getId());
      startActivity(intent);
      return true;
    } else if (item.getItemId() == R.id.miImageScan) {
      return true;   // no action here
    } else if (item.getItemId() == R.id.miLogging) {
      intent = new Intent(this, MainActivity.class);
      startActivity(intent);
      return true;
    }
    else {
      return super.onOptionsItemSelected(item);
    }
  }
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_capture);
    
    // ActionBar related requirements
    Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(myToolbar);
    
    scanSnapshotBtn = (Button) findViewById(R.id.btn_scan_snapshot);
    scanBtn = (Button) findViewById(R.id.btn_scan);
    statusBtn = (Button) findViewById(R.id.btn_getstatus);
    fingerImage = (ImageView) findViewById(R.id.finger_image);
    log = (TextView) findViewById(R.id.device_log);
    
    connection=ConnectionManager.getInstance();
    connection.SetChangesListener(this);
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    setDevice();
  }
  
  @Override
  protected void onStop() {
    super.onStop();
    if (scanTask != null) scanTask.cancel(true);
    NBDevice device = connection.GetDevice();
    if(device != null && device.isScanRunning()) {
      android.util.Log.d("pengzhan","captureActivity isScanRunning stop it");
      device.cancelScan();
    }
    enableButtons(true);
  }
  
  @Override
  public void onClick(View v) {
    int id = v.getId();
    if (connection.IsConnected()) {
      if (id == R.id.btn_scan_snapshot) {
        startCapture(ScanType.SNAPSHOT);
      } else if (id == R.id.btn_scan) {
        startCapture(ScanType.ONE_FINGERPRINT);
      } else if (id == R.id.btn_getstatus) {
        getStatus();
      }
    }
  }
  
  private void setDevice() {
    enableButtons(connection.IsConnected());
    log.setText(connection.IsConnected() ? getString(R.string.scan_start) : getString(R.string.device_not_connected));
    fingerImage.setImageResource(R.drawable.scan_process_initial);
  }
  
  private void enableButtons(boolean en) {
    scanBtn.setEnabled(en);
    scanSnapshotBtn.setEnabled(en);
    statusBtn.setEnabled(en);
  }
  
  private void startCapture(ScanType scanType) {
    scanTask = new ScanTask(scanType);
    scanTask.execute(connection.GetDevice().getSupportedScanFormats()[0]);
  }
  
  private static Bitmap convertToBitmap(NBDeviceScanFormatInfo formatInfo, byte[] image) {
    IntBuffer buf = IntBuffer.allocate(image.length);
    for (byte pixel : image) {
      int grey = pixel & 0x0ff;
      buf.put(Color.argb(255, grey, grey, grey));
    }
    return Bitmap.createBitmap(buf.array(), formatInfo.getWidth(), formatInfo.getHeight(), Config.ARGB_8888);
  }
  
  private void cancelCapture() {
    new AsyncTask<Void, Void, String>() {
      
      @Override
      protected void onPreExecute() {
        enableButtons(false);
      }
      
      @Override
      protected String doInBackground(Void... params) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
        try {
          connection.GetDevice().cancelScan();
          return null;
        } catch (RuntimeException e) {
          e.printStackTrace();
          return e.getMessage();
        }
      }
      
      @Override
      protected void onPostExecute(String msg) {
        enableButtons(true);
        if (msg != null && !"".equals(msg)) {
          log.setText(msg);
        }
      }
    }.execute();
  }
  
  private void getStatus() {
    new AsyncTask<Void, Void, String>() {
      
      @Override
      protected void onPreExecute() {
        enableButtons(false);
      }
      
      @Override
      protected String doInBackground(Void... params) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        try {
          return connection.GetDevice().getState().toString();
        } catch (RuntimeException e) {
          e.printStackTrace();
          return e.getMessage();
        }
      }
      
      @Override
      protected void onPostExecute(String msg) {
        enableButtons(true);
        log.setText(getString(R.string.device_state));
        log.append(" ");
        log.append(msg);
      }
    }.execute();
  }
  
  private enum ScanType {
    SNAPSHOT,
    ONE_FINGERPRINT
  }
  
  private class ScanProgress {
    private String message;
    private Bitmap image;
    
    ScanProgress(NBDeviceScanResult result, int fingerprintDetectValue) {
      this(result.getStatus(), fingerprintDetectValue, result.getFormat(), result.getImage());
    }
    
    ScanProgress(NBDeviceScanPreviewEvent eventDetails) {
      this(eventDetails.getStatus(), eventDetails.getFingerDetectValue(), eventDetails.getFormat(), eventDetails.getImage());
    }
    
    ScanProgress(NBDeviceScanStatus status, int fingerprintDetectValue, NBDeviceScanFormatInfo formatInfo, byte[] image) {
      this(String.format("%s %s, %s %d", getString(R.string.scan_status), status, getString(R.string.finger_detect_value), fingerprintDetectValue), convertToBitmap(formatInfo, image));
    }
    
    ScanProgress(String message, Bitmap image) {
      this.message = message;
      this.image = image;
    }
    
    ScanProgress(String message) {
      this(message, null);
    }
    
    String getMessage() {
      return message;
    }
    
    Bitmap getImage() {
      return image;
    }
  }
  
  private class ScanTask extends AsyncTask<NBDeviceScanFormatInfo, ScanProgress, ScanProgress> implements NBDeviceScanPreviewListener {
    private ScanType scanType;
    
    ScanTask(ScanType scanType) {
      this.scanType = scanType;
    }
    
    @Override
    protected void onPreExecute() {
      enableButtons(false);
      log.setText(R.string.scan_in_progress);
      fingerImage.setImageResource(R.drawable.scan_process_initial);
    }
    
    @Override
    protected ScanProgress doInBackground(NBDeviceScanFormatInfo... params) {
      Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
      try {
        NBDevice device = connection.GetDevice();
        NBDeviceScanFormatInfo format = params[0];
        if (scanType == ScanType.SNAPSHOT)
          return new ScanProgress(device.scan(format), device.getFingerDetectValue());
        return new ScanProgress(device.scanEx(format, 60000, this), device.getFingerDetectValue());
      } catch (Throwable e) {
        e.printStackTrace();
        publishProgress(new ScanProgress(String.format("%s %s", getString(R.string.scan_failed), e.getMessage())));
        return null;
      }
    }
    
    @Override
    protected void onProgressUpdate(ScanProgress... msg) {
      ScanProgress progress = msg[0];
      updateView(progress);
    }
    
    @Override
    protected void onPostExecute(ScanProgress fp) {
      enableButtons(true);
      if (fp != null) {
        updateView(fp);
      } else {
        fingerImage.setImageResource(R.drawable.scan_process_fail);
      }
    }
    
    @Override
    public void preview(NBDeviceScanPreviewEvent event) {
      publishProgress(new ScanProgress(event));
    }
    
    private void updateView(ScanProgress progress) {
      fingerImage.setImageBitmap(progress.getImage());
      log.setText(progress.getMessage());
    }
  }
  
  public void onDeviceChanged() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        setDevice();
      }
    });
  }
  
  public void onConnecting() {}
}

