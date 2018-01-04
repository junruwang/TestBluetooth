package com.guoguang.testbluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.util.ArraySet;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemClickListener {
    private Button search, pair,cancelPair, connect, close, front, back, left, right, stop, light, gripper, musicChange, modeChange;
    private ListView showDevices, showPairDevices;
    private TextView confirmedDevice, state;

    private BluetoothAdapter mBluetoothAdapter;

    //搜索设备ListView的适配器
    private ArrayAdapter<String> devicesAdapter;

    //已配对设备ListView的适配器
    private ArrayAdapter<String> pairDevicesAdapter;

    //搜索到的设备信息
    private List<String> bluetoothDevices = new ArrayList<String>();

    //已配对的设备信息
    private List<String> pairBluetoothDevices = new ArrayList<String>();

    //选中的蓝牙设备
    private BluetoothDevice selectDevice = null;

    //选中的客户端
    private BluetoothSocket clientSocket = null;

    private OutputStream os;

    private ConnectThread connectThread = null;

    private ConnectedThread connectedThread = null;

    private ConnectStateThread connectStateThread = null;

    //private AcceptThread acceptThread;

    private final UUID MY_UUID = UUID
            .fromString("00001101-0000-1000-8000-00805F9B34FB");//00001101-0000-1000-8000-00805F9B34FB 9268a265-8d91-4ab9-a109-63b466a633e1

    private final String connectName = "Bluetooth_Socket";
    private static final String TAG = "myBluetooth";

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    state.setText((String) msg.obj);
                    break;
                case 2:
                    showDialog((String) msg.obj);
                    break;
            }

        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(this.getResources().getConfiguration().orientation== Configuration.ORIENTATION_PORTRAIT){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        setContentView(R.layout.activity_main);

        search = (Button) findViewById(R.id.search);
        pair = (Button) findViewById(R.id.pair);
        cancelPair=(Button)findViewById(R.id.cancelPair);
        connect = (Button) findViewById(R.id.connect);
        close = (Button) findViewById(R.id.close);
        front = (Button) findViewById(R.id.front);
        back = (Button) findViewById(R.id.back);
        left = (Button) findViewById(R.id.left);
        right = (Button) findViewById(R.id.right);
        stop = (Button) findViewById(R.id.stop);
        light = (Button) findViewById(R.id.light);
        gripper = (Button) findViewById(R.id.gripper);
        musicChange = (Button) findViewById(R.id.musicChange);
        modeChange = (Button) findViewById(R.id.modeChange);

        showDevices = (ListView) findViewById(R.id.showDevices);
        showPairDevices = (ListView) findViewById(R.id.showPairDevices);
        confirmedDevice = (TextView) findViewById(R.id.confirmedDevice);
        state = (TextView) findViewById(R.id.state);

        search.setOnClickListener(this);
        pair.setOnClickListener(this);
        cancelPair.setOnClickListener(this);
        connect.setOnClickListener(this);
        close.setOnClickListener(this);
        front.setOnClickListener(this);
        back.setOnClickListener(this);
        left.setOnClickListener(this);
        right.setOnClickListener(this);
        stop.setOnClickListener(this);
        light.setOnClickListener(this);
        gripper.setOnClickListener(this);
        musicChange.setOnClickListener(this);
        modeChange.setOnClickListener(this);

        openBluetooth();
        setBluetoothReceiver();
        connectStateThread = new ConnectStateThread();
        connectStateThread.start();
    }


    private class BluetoothReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            devicesAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, android.R.id.text1, bluetoothDevices);
            showDevices.setAdapter(devicesAdapter);
            showDevices.setOnItemClickListener(MainActivity.this);
            if (intent.getAction().equals(BluetoothDevice.ACTION_FOUND)) {
                Log.d(TAG, "找到新设备");
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //删除重复
                for (int i = 0; i < bluetoothDevices.size(); i++) {
                    //判断IP地址
                    if (bluetoothDevices.get(i).substring(bluetoothDevices.get(i).indexOf(":") + 1).equals(device.getAddress())) {
                        bluetoothDevices.remove(i);
                        break;
                    }
                }
                bluetoothDevices.add(device.getName() + ":" + device.getAddress());//+ ":" + bluetoothDevice.getBondState()
            }


        }
    }

    private void setBluetoothReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        registerReceiver(new BluetoothReceiver(), intentFilter);

    }


    /**
     * 开启蓝牙设备
     */
    private void openBluetooth() {
        //获取蓝牙适配器
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        //开启蓝牙
        if (!mBluetoothAdapter.isEnabled()) {
            //Intent enabler = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            //startActivityForResult(enabler, RESULT_FIRST_USER);
            //设置蓝牙设备可见
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);//默认120秒
            intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 200);
            startActivity(intent);
            //直接开启，不提示
            //mBluetoothAdapter.enable();
        }

    }

    /**
     * 显示已配对设备列表
     */
    private void setPairDevicesView() {
        pairDevicesAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, android.R.id.text1, pairBluetoothDevices);
        showPairDevices.setAdapter(pairDevicesAdapter);
        showPairDevices.setOnItemClickListener(MainActivity.this);
        pairBluetoothDevices.removeAll(pairBluetoothDevices);
        Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
        if (devices != null && devices.size() > 0) {
            for (BluetoothDevice bluetoothDevice : devices) {
                //删除第二次搜索中与前一次的重复设备
                for (int i = 0; i < pairBluetoothDevices.size(); i++) {
                    //判断IP地址
                    if (pairBluetoothDevices.get(i).substring(pairBluetoothDevices.get(i).indexOf(":") + 1).equals(bluetoothDevice.getAddress())) {
                        pairBluetoothDevices.remove(i);
                        break;
                    }
                }
                pairBluetoothDevices.add(bluetoothDevice.getName() + ":" + bluetoothDevice.getAddress());//+ ":" + bluetoothDevice.getBondState()
            }
        }
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
        String deviceAddress = "";
        String info = "";
        //选择设备配对
        if (parent == showDevices) {
            info = devicesAdapter.getItem(position);
            deviceAddress = info.substring(info.indexOf(":") + 1).trim();
            BluetoothBond("createBond",deviceAddress);

        }

        //选择已配对设备连接
        if (parent == showPairDevices) {
            info = pairDevicesAdapter.getItem(position);
            confirmedDevice.setText(info);
            deviceAddress = info.substring(info.indexOf(":") + 1).trim();
            if (deviceAddress.length() > 17) {
                deviceAddress = deviceAddress.substring(0, 17);
            }
            Log.d(TAG, "address=" + deviceAddress);
            selectDevice = mBluetoothAdapter.getRemoteDevice(deviceAddress);

        }


    }

    private void BluetoothBond(String cmd,String deviceAddress){
        try {
            //如果想要取消已经配对的设备，只需要将creatBond改为removeBond
            Method method = BluetoothDevice.class.getMethod(cmd);
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(deviceAddress);
            method.invoke(device);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 提示框
     *
     * @param msg
     */
    private void showDialog(String msg) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
        dialog.setMessage(msg);
        dialog.setNegativeButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        dialog.show();
    }

    /**
     * 蓝牙连接线程
     */
    private class ConnectThread extends Thread {
        private boolean isDeviceSelected = false;//是否已选择设备
        private boolean isConnecting = false;

        public ConnectThread() {

            BluetoothSocket tmp = null;
            //禁止未关闭现有连接前开启新连接
            if (clientSocket != null) {
                if (clientSocket.isConnected()) {
                    isConnecting = true;
                    String info = "已连接一个蓝牙设备，要重新连接请先关闭现有连接";
                    Message msg = new Message();
                    msg.obj = info;
                    msg.what = 2;
                    handler.sendMessage(msg);
                }
            }

            if (selectDevice != null) {
                try {
                    tmp = selectDevice.createRfcommSocketToServiceRecord(MY_UUID);
                    isDeviceSelected = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                clientSocket = tmp;

            } else {
                //未选择设备
                isDeviceSelected = false;
                String info = "请选择一个蓝牙设备连接";
                Message msg = new Message();
                msg.obj = info;
                msg.what = 2;
                handler.sendMessage(msg);
            }
            Log.d(TAG,"isDevice"+isDeviceSelected);
            Log.d(TAG,"isConnecting"+isConnecting);

        }

        @Override
        public void run() {

            if (isDeviceSelected && (!isConnecting)) {
                try {
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    Log.d(TAG,"connect start");
                    clientSocket.connect();
                    boolean a=clientSocket.isConnected();
                    if (clientSocket.isConnected()) {
                        Log.d(TAG, "connect success");
                    }
                } catch (IOException e) {
                    if (clientSocket != null) {
                        try {
                            clientSocket.close();
                            clientSocket = null;
                        } catch (IOException closeException) {
                        }
                    }

                }
            }
        }

        public void close() {
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                    clientSocket = null;
                    Log.d(TAG, "connect close");
                } else {
                    Log.d(TAG, "clientSocket is null");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    /**
     * 蓝牙连接线程
     */
    /*private class ConnectThread extends Thread {
        private int noDevice = 1;//是否已选择设备

        public ConnectThread() {
            BluetoothSocket tmp = null;
            if (selectDevice != null) {
                try {
                    tmp = selectDevice.createRfcommSocketToServiceRecord(MY_UUID);
                    noDevice = 0;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                clientSocket = tmp;
            } else {
                noDevice = 1;
                AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
                dialog.setMessage("请选择一个蓝牙设备连接");
                dialog.setNegativeButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                dialog.show();
            }

        }

        @Override
        public void run() {
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
            if (noDevice == 0) {
                try {
                    clientSocket.connect();
                    if (clientSocket.isConnected()) {
                        Log.d(TAG, "connect success");
                    }
                } catch (IOException e) {
                    try {
                        clientSocket.close();
                    } catch (IOException closeException) {
                    }
                }
            }
        }

        public void close() {
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                    Log.d(TAG, "connect close");
                } else {
                    Log.d(TAG, "clientSocket is null");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }*/

    /**
     * 蓝牙传输线程（连接状态）
     */
    private class ConnectedThread extends Thread {
        private String msg;

        public ConnectedThread(String cmd) {
            msg = cmd;
        }

        @Override
        public void run() {
            if ((selectDevice != null) && (clientSocket != null) && clientSocket.isConnected()) {
                try {
                    os = clientSocket.getOutputStream();
                    os.write(msg.getBytes());
                    Log.d(TAG, "write success");
                } catch (IOException e) {
                    //receiveInfo.setText("I/O端口关闭，连接断开");
                    Log.d(TAG, "write fail1");
                    e.printStackTrace();
                }
            } else {
                String info = "请先连接蓝牙设备";
                Message msg = new Message();
                msg.obj = info;
                msg.what = 2;
                handler.sendMessage(msg);
            }

        }
    }

    private class ConnectStateThread extends Thread {
        @Override
        public void run() {
            while (true) {
                Message msg = new Message();
                if (selectDevice != null) {
                    if (clientSocket != null) {
                        if (clientSocket.isConnected()) {
                            msg.obj = "已连接";
                        } else {
                            msg.obj = "断开";
                        }
                    } else {
                        msg.obj = "未连接";
                    }
                } else {
                    msg.obj = "未选择设备";
                }
                msg.what = 1;
                handler.sendMessage(msg);
                try {
                    Thread.sleep(1000 * 3);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    @Override
    public void onClick(View v) {
        String cmd = "";
        switch (v.getId()) {
            case R.id.search:
                bluetoothDevices.removeAll(bluetoothDevices);
                mBluetoothAdapter.startDiscovery();
                break;
            case R.id.pair:
                setPairDevicesView();
                break;
            case R.id.cancelPair:
                if(selectDevice!=null){
                    String address=selectDevice.getAddress();
                    BluetoothBond("removeBond",address);
                    setPairDevicesView();
                    confirmedDevice.setText("无");
                }else {
                    String info="请选择要取消配对的设备";
                    Message msg=new Message();
                    msg.obj=info;
                    msg.what=2;
                    handler.sendMessage(msg);
                }
                break;

            case R.id.connect:
                connectThread = new ConnectThread();
                connectThread.start();
                break;
            case R.id.close:
                connectThread.close();
                break;
            case R.id.front:
                cmd = "AA0101FF";
                connectedThread = new ConnectedThread(cmd);
                connectedThread.start();
                break;
            case R.id.stop:
                cmd = "AA0100FF";
                connectedThread = new ConnectedThread(cmd);
                connectedThread.start();
                break;
            case R.id.back:
                cmd = "AA0102FF";
                connectedThread = new ConnectedThread(cmd);
                connectedThread.start();
                break;
            case R.id.left:
                cmd = "AA0103FF";
                connectedThread = new ConnectedThread(cmd);
                connectedThread.start();
                break;
            case R.id.right:
                cmd = "AA0104FF";
                connectedThread = new ConnectedThread(cmd);
                connectedThread.start();
                break;
            case R.id.light:
                cmd = "AA0201FF";
                connectedThread = new ConnectedThread(cmd);
                connectedThread.start();
                break;
            case R.id.gripper:
                cmd = "AA0301FF";
                connectedThread = new ConnectedThread(cmd);
                connectedThread.start();
                break;
            case R.id.musicChange:
                cmd = "AA0401FF";
                connectedThread = new ConnectedThread(cmd);
                connectedThread.start();
                break;
            case R.id.modeChange:
                cmd = "AA0501FF";
                connectedThread = new ConnectedThread(cmd);
                connectedThread.start();
                break;
        }
    }

}
