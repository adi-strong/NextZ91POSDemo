package com.zcs.nbbiometricdemo;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.nextbiometrics.biometrics.NBBiometricsContext;
import com.nextbiometrics.biometrics.NBBiometricsExtractResult;
import com.nextbiometrics.biometrics.NBBiometricsFingerPosition;
import com.nextbiometrics.biometrics.NBBiometricsIdentifyResult;
import com.nextbiometrics.biometrics.NBBiometricsSecurityLevel;
import com.nextbiometrics.biometrics.NBBiometricsStatus;
import com.nextbiometrics.biometrics.NBBiometricsTemplate;
import com.nextbiometrics.biometrics.NBBiometricsTemplateType;
import com.nextbiometrics.biometrics.event.NBBiometricsScanPreviewEvent;
import com.nextbiometrics.biometrics.event.NBBiometricsScanPreviewListener;
import com.nextbiometrics.devices.NBDevice;
import com.nextbiometrics.devices.NBDeviceScanStatus;
import com.nextbiometrics.system.NextBiometricsException;

import java.util.AbstractMap;
import java.util.LinkedList;
import java.util.List;

/*
 *  The EV Activity is meant for enrolment of Fingers and its validation.
 *  While scanner related functions are executed in a thread, UI-related actions like
 *  showing a message need to be executed on the UI thread. Therefore, several helper
 *  function are implemented to show messages and modify graphics. Further information
 *  on each step is provided below.
 *
 * */
public class EVActivity extends AppCompatActivity implements OnClickListener, ConnectionManager.ChangesListener {
  
  Context ActivityContext;
  
  private TextView log;
  private ImageView fingerImage;
  private Button btn_enroll;
  private Button btn_identify;
  private Button btn_cancel;
  private Button btn_clearDB;
  
  private ConnectionManager connection;
  
  private List<AbstractMap.SimpleEntry<Object, NBBiometricsTemplate>> templateDatabase;
  
  /* ActionBar related function */
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }
  
  /* ActionBar related function */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle item selection
    Intent intent;
    if (item.getItemId() == R.id.miEnrollIdentify) {
      return true;  // no action - same activity
    } else if (item.getItemId() == R.id.miImageScan) {
      intent = new Intent(this, CaptureActivity.class);
      startActivity(intent);
      return true;
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
    setContentView(R.layout.activity_ev);
    
    ActivityContext = this.getApplicationContext();
    
    // ActionBar related requirements
    Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(myToolbar);
    
    fingerImage = (ImageView) findViewById(R.id.finger_image);
    log = (TextView) findViewById(R.id.device_log);
    
    btn_enroll = (Button) findViewById(R.id.enrollBtn);
    btn_enroll.setOnClickListener(this);
    
    btn_identify = (Button) findViewById(R.id.identifyBtn);
    btn_identify.setOnClickListener(this);
    
    btn_cancel = (Button) findViewById(R.id.cancelBtn);
    btn_cancel.setOnClickListener(this);
    
    btn_clearDB = (Button) findViewById(R.id.clearDBBtn);
    btn_clearDB.setOnClickListener(this);
    
    // Create template list
    templateDatabase = new LinkedList<>();
    
    connection = ConnectionManager.getInstance();
    connection.SetChangesListener(this);
    SetAllButtonsActive(connection.IsConnected());
  }
  
  // various helper functions to modify the UI
  public void showLog(final String txt) {
    this.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        log.setText(txt);
      }
    });
  }
  
  public void showImage(final int res) {
    this.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        fingerImage.setImageResource(res);
      }
    });
  }
  
  public void showMessage(final String message) {
    this.runOnUiThread(new Runnable() {
      
      @Override
      public void run() {
        Toast.makeText(ActivityContext, message, Toast.LENGTH_SHORT).show();
      }
    });
  }
  
  public void SetAllButtonsActive(final boolean state) {
    this.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        btn_enroll.setEnabled(state);
        btn_identify.setEnabled(state);
        btn_clearDB.setEnabled(state);
        btn_cancel.setEnabled(!state);
      }
    });
  }
  
  private String getPreviewString(NBDeviceScanStatus status) {
    switch (status) {
      case NO_FINGER:
        return "Mettre le doigt sur";
      case NOT_REMOVED:
        return "Lever le doigt vers le haut";
      case BAD_SIZE:
        return "Appuyez fermement";
      case BAD_QUALITY:
        return "Appuyez fermement";
      case CANCELED:
        return "Annulé";
      case NONE:
        return "Aucune Empreinte";
      case OK:
        return "Image capturée";
      case TIMEOUT:
        return "Expiration du délai d'opération";
    }
    return "";
  }
  
  private class ScanPreview implements NBBiometricsScanPreviewListener {
    @Override
    public void preview(NBBiometricsScanPreviewEvent eventArgs) {
      showLog(getPreviewString(eventArgs.getScanStatus()));
    }
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    setDevice();
  }
  
  @Override
  protected void onStop() {
    super.onStop();
    NBDevice device = connection.GetDevice();
    if(device != null && device.isScanRunning()) {
      android.util.Log.d("pengzhan","isScanRunning stop it");
      device.cancelScan();
    }
  }
  
  @Override
  public void onClick(View v) {
    final int id = v.getId();
    setDevice();
    if (!connection.IsConnected()) return;
    
    final Thread processingThread = new Thread(new Runnable() {
      @Override
      public void run() {
        NBDevice device = connection.GetDevice();
        Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);   // increase thread priority
        NBBiometricsContext context = null;
        NBBiometricsScanPreviewListener preview = new ScanPreview();
        try {
          showImage(R.drawable.ev_enroll);
          context = new NBBiometricsContext(device);
          
          if (id == R.id.enrollBtn) {
            // Finger enrolment
            SetAllButtonsActive(false);
            showLog("Placez le doigt sur le scanner pour vous inscrire");
            
            // Extract Finger information
            NBBiometricsExtractResult extractResult =
              context.extract(NBBiometricsTemplateType.PROPRIETARY,
                NBBiometricsFingerPosition.UNKNOWN,
                device.getSupportedScanFormats()[0],
                preview);
            
            
            if (extractResult.getStatus() == NBBiometricsStatus.OK)
            {
              NBBiometricsTemplate template = extractResult.getTemplate();
              String FingerID = "Doigt " + (templateDatabase.size() + 1);
              templateDatabase.add(new AbstractMap.SimpleEntry<Object, NBBiometricsTemplate>(FingerID, template));
              
              // use helper functions to modify UI - Finger enrolled
              showLog(FingerID + " inscrit");
              showImage(R.drawable.ev_success);
            } else {
              showLog(extractResult.getStatus().toString());
            }
            
            SetAllButtonsActive(true);
            
          } else if (id == R.id.identifyBtn) {
            // Finger identification
            if (templateDatabase.size() == 0) {
              showLog("Select operation");
              showMessage("Fingerprint database empty");
              return;
            }
            SetAllButtonsActive(false);
            showLog("Placez le doigt sur le scanner pour identifier");
            
            NBBiometricsIdentifyResult identifyResult =
              context.identify(NBBiometricsTemplateType.PROPRIETARY,
                NBBiometricsFingerPosition.UNKNOWN,
                device.getSupportedScanFormats()[0],
                10000,
                preview,
                templateDatabase.iterator(),
                NBBiometricsSecurityLevel.NORMAL);
            
            if (identifyResult.getStatus() == NBBiometricsStatus.OK) {
              // use helper functions to highlight existing Finger
              showImage(R.drawable.ev_success);
              showLog(identifyResult.getTemplateId() + " identifié");
            } else if (identifyResult.getStatus() == NBBiometricsStatus.MATCH_NOT_FOUND) {
              // use helper functions to modify UI - Finger not identified
              showImage(R.drawable.ev_wrong);
              showLog("Doigt non identifié");
            } else if (identifyResult.getStatus() != NBBiometricsStatus.CANCELED) {
              showLog("Annulé");
            }
            SetAllButtonsActive(true);
            
          } else if (id == R.id.cancelBtn) {
            device.cancelScan();
          } else if (id == R.id.clearDBBtn) {
            templateDatabase.clear();
            showMessage("Base de données temporaire effacée.");
          }
          
        } catch (NextBiometricsException ex) {
          showMessage("ERROR - " + ex.toString());
          ex.printStackTrace();
          
        } catch (Throwable ex) {
          showMessage("ERROR2 - " + ex.toString());
          ex.printStackTrace();
          
        } finally {
          SetAllButtonsActive(true);
          if (context != null) context.dispose();
        }
      }
    });
    processingThread.start();
  }
  
  private void setDevice() {
    SetAllButtonsActive(connection.IsConnected());
    log.setText(connection.IsConnected() ? getString(R.string.scan_start) : getString(R.string.device_not_connected));
    fingerImage.setImageResource(R.drawable.scan_process_initial);
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