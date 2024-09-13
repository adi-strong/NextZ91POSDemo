package com.zcs.nbbiometricdemo;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.os.Process;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.nextbiometrics.devices.NBDevice;
import com.nextbiometrics.devices.NBDeviceType;
import com.nextbiometrics.devices.NBDevices;
import com.nextbiometrics.devices.event.NBDevicesDeviceChangedEvent;
import com.nextbiometrics.devices.event.NBDevicesDeviceChangedListener;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;

/* A singleton class for sharing connected device between different activities */
public class ConnectionManager extends Activity implements NBDevicesDeviceChangedListener
{
    private static final String TAG = "ConnectionManager";

    //z91m
    private static final String DEFAULT_SPI_NAME = "/dev/arafp0";
    private static final int DEFAULT_PIN_OFFSET  = 343;
    private static final int DEFAULT_AWAKE_PIN_NUMBER = 14;
    private static final int DEFAULT_RESET_PIN_NUMBER = 13;
    private static final int DEFAULT_CHIP_SELECT_PIN_NUMBER = 31;

    //z80
//    private static final String SPI_NAME = "/dev/spidev1.0";
//    private static final int PIN_OFFSET  = 73;
//    private static final int AWAKE_PIN_NUMBER = PIN_OFFSET + 112;
//    private static final int RESET_PIN_NUMBER = PIN_OFFSET + 177;
//    private static final int CHIP_SELECT_PIN_NUMBER = PIN_OFFSET + 26;

    //Z92C A13
//    private static final String DEFAULT_SPI_NAME = "/dev/spidev0.0";
//    private static final int DEFAULT_PIN_OFFSET  = 329;
//    private static final int DEFAULT_AWAKE_PIN_NUMBER = 112;
//    private static final int DEFAULT_RESET_PIN_NUMBER = 177;
//    private static final int DEFAULT_CHIP_SELECT_PIN_NUMBER = 156;

    //Z90SC71
//    private static final String DEFAULT_SPI_NAME = "/dev/spidev0.0";
//    private static final int DEFAULT_PIN_OFFSET  = 911;
//    private static final int DEFAULT_AWAKE_PIN_NUMBER = 16;
//    private static final int DEFAULT_RESET_PIN_NUMBER = 58;
//    private static final int DEFAULT_CHIP_SELECT_PIN_NUMBER = 10;

    public static String getSystemProperty(String key) {
        String result = null;
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class);
            result = (String) get.invoke(c, key);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private int getSystemPropertyIntegerValue(String key, int defaultValue) {
        String temp = getSystemProperty(key);
        int result = defaultValue;
        try{
            result = Integer.parseInt(temp);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private String SPI_NAME = DEFAULT_SPI_NAME;
    private int PIN_OFFSET;
    private int AWAKE_PIN_NUMBER;
    private int RESET_PIN_NUMBER;
    private int CHIP_SELECT_PIN_NUMBER;
    private void initPin() {
        String tempSpiName = getSystemProperty("ro.next_spi");
        if(!TextUtils.isEmpty(tempSpiName)){
            SPI_NAME = tempSpiName;
        } else {
            SPI_NAME = DEFAULT_SPI_NAME;
        }
        PIN_OFFSET = getSystemPropertyIntegerValue("ro.next_pin_offset", DEFAULT_PIN_OFFSET);
        AWAKE_PIN_NUMBER = PIN_OFFSET + getSystemPropertyIntegerValue("ro.next_pin_wake", DEFAULT_AWAKE_PIN_NUMBER);
        RESET_PIN_NUMBER = PIN_OFFSET + getSystemPropertyIntegerValue("ro.next_pin_rst", DEFAULT_RESET_PIN_NUMBER);
        CHIP_SELECT_PIN_NUMBER = PIN_OFFSET + getSystemPropertyIntegerValue("ro.next_pin_cs", DEFAULT_CHIP_SELECT_PIN_NUMBER);
    }

// DB410C
//
//    private static final String SPI_NAME = "/dev/spidev0.0";
//    private static final int PIN_OFFSET  = 902;
//    private static final int AWAKE_PIN_NUMBER = PIN_OFFSET + 69;
//    private static final int RESET_PIN_NUMBER = PIN_OFFSET + 12;
//    private static final int CHIP_SELECT_PIN_NUMBER = PIN_OFFSET + 18;

//TPS360 telpo
    /*
    private static final String SPI_NAME = "/dev/spidev0.0";
    private static final int PIN_OFFSET  = 911;
    private static final int AWAKE_PIN_NUMBER = PIN_OFFSET + 95;
    private static final int RESET_PIN_NUMBER = PIN_OFFSET + 58;
    private static final int CHIP_SELECT_PIN_NUMBER = PIN_OFFSET + 10;
*/
    public enum Interface {
        USB, SPI
    }

    private static ConnectionManager instance = null;

    private Interface anInterface;
    private static NBDevice device;
    private Context context;
    private ChangesListener listener;
    private boolean connectInProgress;
    private Thread processingThread;


    private ConnectionManager() {
        this.listener = null;
        this.device = null;
        this.context = null;
        this.connectInProgress = false;
        initPin();
    }

    public static ConnectionManager getInstance() {
        if (instance == null) {
            instance= new ConnectionManager();
        }
        return instance;
    }

    public void Connect(final Interface iface)
    {
        try {
            if (!NBDevices.isInitialized()) {
                Log.d(TAG, "Initializing NBDevices");
                NBDevices.initialize(context, this);
            }
        }
        catch (Throwable ex) {
            Log.d(TAG, "NBDevices.initialize() failed with following exception: " + ex.toString());
            return;
        }

        processingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (connectInProgress)  SystemClock.sleep(100); // wait until a previous
                connectInProgress = true;
                if (!IsConnected())
                {
                    listener.onConnecting();
                    anInterface = iface;
                    try {
                        if (anInterface == Interface.SPI) {
                            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);   // increase thread priority
                            Log.d(TAG, "Connecting to SPI ...");
                            device = NBDevice.connectToSpi(SPI_NAME, AWAKE_PIN_NUMBER, RESET_PIN_NUMBER, CHIP_SELECT_PIN_NUMBER, NBDevice.DEVICE_CONNECT_TO_SPI_SKIP_GPIO_INIT_FLAG);

                            if(device != null && device.getType() == NBDeviceType.NB65210S) {
                                final String paths = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/NBData/" + device.getSerialNumber() + "_calblob.bin";
                                File file = new File(paths);
                                if(file.exists()) {
                                    int size = (int) file.length();
                                    byte[] bytes = new byte[size];
                                    try {
                                        BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file));
                                        buf.read(bytes, 0, bytes.length);
                                        buf.close();
                                    }
                                    catch (IOException ex) {}
                                    device.SetBlobParameter(NBDevice.BLOB_PARAMETER_CALIBRATION_DATA, bytes);
                                }
                                else {
                                    throw new Exception("Missing compensation data - " + paths);
                                }
                            }




                        } else if (anInterface == Interface.USB) {
                            Log.d(TAG, "Connecting to USB ...");
                            NBDevice[] devices = NBDevices.getDevices();
                            if (devices.length != 0) {
                                device = devices[0];
                            }
                        }
                    } catch (Throwable ex) {
                        if (anInterface == Interface.SPI) {
                            Log.d(TAG, "NBDevice.connectToSpi() failed with following exception: " + ex.toString());
                        } else if (anInterface == Interface.USB) {
                            Log.d(TAG, "NBDevices.getDevices() failed with following exception: " + ex.toString());
                        }
                    }
                    Log.d(TAG, device != null ? "Device connected. " : "No device found");
                    listener.onDeviceChanged();
                }
                connectInProgress = false;
            }
        });
        processingThread.start();
    }

    public void Connect()
    {
        Connect(Interface.SPI);
        if (!IsConnected()) Connect(Interface.USB);
    }

    public void Reconnect(Interface iface) {
        if (IsConnected()) {  // disconnect current device
            Disconnect();
            Log.d(TAG, "Device disconnected");
        }
        Connect(iface);
    }

    public void Reconnect() {
        if (IsConnected()) {  // disconnect current device
            Disconnect();
            Log.d(TAG, "Device disconnected");
        }
        Connect();
    }

    public void Disconnect()
    {
        if (device != null) {
            if (device.isScanRunning()) device.cancelScan();
            device.dispose();
            device = null;
        }
    }

    public NBDevice GetDevice() {
        return device;
    }

    public boolean IsConnected() {
        return device!= null;
    }

    public void Terminate() {
        Disconnect();
        if (NBDevices.isInitialized()) NBDevices.terminate();
    }

    public void SetApplicationContext (Context context) {
        this.context = context;
    }

    @Override
    public void added(NBDevicesDeviceChangedEvent event) {
        if (anInterface == Interface.USB) {
            Log.d(TAG, "Added USB device\n");
            device = event.getDevice();
            listener.onDeviceChanged();
        }
    }

    @Override
    public void removed(NBDevicesDeviceChangedEvent event) {
        if (anInterface == Interface.USB) {
            Log.d(TAG, "Removed USB device\n");
            device = null;
            listener.onDeviceChanged();
        }
    }

    // Assign the listener implementing events interface that will receive the events
    public void SetChangesListener(ChangesListener listener) {
        this.listener = listener;
    }
    public interface ChangesListener
    {
        void onDeviceChanged();
        void onConnecting();
    }

}