package com.zcs.nbbiometricdemo;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.nextbiometrics.biometrics.NBBiometricsLibrary;
import com.nextbiometrics.devices.NBDevice;
import com.nextbiometrics.devices.NBDevicesLibrary;

public class MainActivity extends AppCompatActivity implements OnClickListener, ConnectionManager.ChangesListener {
  private static final String TAG = "MainActivity";
  
  private static final int APP_VERSION_MAJOR = 1;
  private static final int APP_VERSION_MINOR = 2;
  
  public static ConnectionManager connection;
  
  private TextView log;
  
  /* ActionBar related function */
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }
  
  /* ActionBar related function */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    Intent intent;
    
    if (item.getItemId() == R.id.miEnrollIdentify) {
      if (!connection.IsConnected()) {
        Toast.makeText(this, "Option not available. No device connected.", Toast.LENGTH_SHORT).show();
      }
      intent = new Intent(this, EVActivity.class);
      startActivity(intent);
      return true;
    } else if (item.getItemId() == R.id.miImageScan) {
      if (!connection.IsConnected()) {
        Toast.makeText(this, "Option not available. No device connected.", Toast.LENGTH_SHORT).show();
      }
      intent = new Intent(this, CaptureActivity.class);
      startActivity(intent);
      return true;
    } else if (item.getItemId() == R.id.miLogging) {
      connection.Reconnect();
      //setLog(getDeviceStatus());
      return true;
    }
    else {
      return super.onOptionsItemSelected(item);
    }
  }
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
    // ActionBar related requirements
    Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(myToolbar);
    
    log = (TextView) findViewById(R.id.device_log);
    
    connection = ConnectionManager.getInstance();
    connection.SetApplicationContext(this);
    connection.SetChangesListener(this);
    connection.Connect();
  }
  
  @Override
  protected void onDestroy() {
    super.onDestroy();
    connection.Terminate();
  }
  
  @Override
  protected void onStart() {
    super.onStart();
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    setLog(getDeviceStatus());
  }
  
  @Override
  protected void onStop() {
    super.onStop();
  }
  
  @Override
  public void onClick(View v) {
    // not used atm
  }
  
  private void setLog(String text) {
    StringBuilder builder = new StringBuilder();
    builder.append("Matching Demo App, version " + APP_VERSION_MAJOR + "." + APP_VERSION_MINOR + "\n");
    builder.append(getString(R.string.devices_library_version)).append(NBDevicesLibrary.getVersion()).append("\n");
    builder.append(getString(R.string.biometrics_library_version)).append(NBBiometricsLibrary.getVersion()).append("\n\n");
    builder.append(text).append("\n\n");
    if (connection.IsConnected())
      builder.append("To continue, please choose one of the options above");
    log.setText(builder.toString());
  }
  
  private String getDeviceStatus() {
    StringBuilder builder = new StringBuilder();
    if (!connection.IsConnected()) {
      builder.append(getString(R.string.device_not_connected)).append("\n");
    } else {
      NBDevice device = connection.GetDevice();
      builder.append(getString(R.string.device_id)).append(device.getId()).append("\n");
      builder.append(getString(R.string.device_manufacturer)).append(device.getManufacturer()).append("\n");
      builder.append(getString(R.string.device_model)).append(device.getModel()).append("\n");
      builder.append(getString(R.string.device_serialnumber)).append(device.getSerialNumber()).append("\n");
      builder.append(getString(R.string.device_firmware_version)).append(device.getFirmwareVersion()).append("\n");
    }
    return builder.toString();
  }
  
  public void onDeviceChanged() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        setLog(getDeviceStatus());
      }
    });
  }
  
  public void onConnecting() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        setLog("Connecting, please wait ...");
      }
    });
  }
}
