package com.example.saik.rocketmultimeterandoscilloscope;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import static com.example.saik.rocketmultimeterandoscilloscope.MainActivity.RECEIVE_JSON;


public class MenuActivity extends AppCompatActivity {


    Button btnVoltmeter1, btnVoltmeter2, btnVoltmeter3, btnAmpmeter, btnOscilloscope;
    private static final String TAG = "rocket";
    BTService btService;
    boolean isBound = false;

    BtDataListener btDataListener = null;

    private static String incomingData = "";

    LocalBroadcastManager broadcastManager;


    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);


        //setup buttons
        btnVoltmeter1 = (Button) findViewById(R.id.btn_voltmeter_1);
        btnVoltmeter1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btService.sendBtMessage(BTService.BtMessageOut.BT_MESSAGE_OUT_VOLTMETER_RANGE_0_TO_0_POINT_5.getValue());
            }
        });

        btnVoltmeter2 = (Button) findViewById(R.id.btn_voltmeter_2);
        btnVoltmeter2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btService.sendBtMessage(BTService.BtMessageOut.BT_MESSAGE_OUT_VOLTMETER_RANGE_0_TO_10.getValue());
            }
        });

        btnVoltmeter3 = (Button) findViewById(R.id.btn_voltmeter_3);
        btnVoltmeter3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btService.sendBtMessage(BTService.BtMessageOut.BT_MESSAGE_OUT_VOLTMETER_RANGE_0_TO_50.getValue());
            }
        });

        btnAmpmeter = (Button) findViewById(R.id.btn_ampmeter);
        btnAmpmeter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btService.sendBtMessage(BTService.BtMessageOut.BT_MESSAGE_OUT_AMPMETER.getValue());
            }
        });

        btnOscilloscope = (Button) findViewById(R.id.btn_oscilloscope);
        btnOscilloscope.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btService.sendBtMessage(BTService.BtMessageOut.BT_MESSAGE_OUT_OSCILLOSCOPE.getValue());
            }
        });
    }



    @Override
    public void onResume()
    {
        super.onResume();
        Log.d(TAG, "MenuActivity:onResume: Binding Service, Registering Receiver");

        btDataListener = new BtDataListener();
        btDataListener.execute();
        bindServiceAndRegisterReceiver();
    }


    @Override
    protected void onPause()
    {
        super.onPause();
        btDataListener.cancel(true);
        tryUnregisteringReceiver();
    }


    @Override
    public void onBackPressed()
    {
        Log.d(TAG, "MenuActivity: onBackPressed: Go back to previous activity since back button is pressed.");
        cleanup();
        btService.sendBtMessage(BTService.BtMessageOut.BT_MESSAGE_OUT_EXIT.getValue());
        finish();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MenuActivity: onDestroy: calling cleanup");

        cleanup();
    }

    private void cleanup()
    {
        tryUnregisteringReceiver();
        terminateBtConnection();

        if(btDataListener != null)
        {
            btDataListener.cancel(true);
        }
    }


    //-----------------------------------------------------------------------------//
    //-------------Broadcast receiver and service binder stuff---------------------//
    //-----------------------------------------------------------------------------//


    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.d(TAG, "remoteControlBubbleWall: mServiceConnection: onServiceConnected");
            btService = ((BTService.MyLocalBinder) service).getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "remoteControlBubbleWall: mServiceConnection: onServiceDisconnected");
            btService = null;
            isBound = false;
        }
    };


    private class BtDataListener extends AsyncTask<Void, Void, Void> {

        String completeMessage = "";
        String startSymbol = "*";
        String endSymbol = "|";


        @Override
        protected Void doInBackground(Void... devices)
        {

            while(!isCancelled())
            {
                if(btService != null)
                {
                    incomingData += btService.checkIncomingBtData();

                    int start = incomingData.indexOf(startSymbol);
                    int end = incomingData.indexOf(endSymbol);
                    int extraStartSymbol = incomingData.substring(start+1, incomingData.length()).indexOf(startSymbol);

                    if(start == -1 && end != -1)
                    {
                        incomingData = incomingData.substring(end + 1, incomingData.length());
                    }
                    else if(start != -1 && end != -1 && start > end)
                    {
                        incomingData = incomingData.substring(start, incomingData.length());
                    }
                    else if(start != -1 && end != -1 && (extraStartSymbol != -1 && extraStartSymbol < end))
                    {
                        incomingData = incomingData.substring(extraStartSymbol + 1, incomingData.length());
                    }
                    else if(start != -1 && end != -1)
                    {
                        completeMessage = incomingData.substring(start+1,end);
                        incomingData = incomingData.substring(end + 1,incomingData.length());
                    }

                    if(!completeMessage.equals(""))
                    {

                       // Log.d(TAG, "BTService: btDataListener: Received Message: " + completeMessage);

                        //If it is not a voltage reading but a control message, such as a message indicating that we are in Voltmeter.
                        if(completeMessage.contains("$")) {
                            processBtControlMessage(Integer.parseInt(completeMessage.substring(1,completeMessage.length())));
                        }

                        completeMessage = "";
                    }
                }

            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Void... values)
        {
            super.onProgressUpdate(values);
        }
    }


    private final BroadcastReceiver btBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if(intent.getAction().equals(RECEIVE_JSON)) {
                String serviceJsonString = intent.getStringExtra("btConnectionStatusUpdate");

                if(serviceJsonString.equals("ConnectionLost")) {
                    Log.d(TAG, "MenuActivity: btBroadcastReceiver: We lost connection with BT");
                    Toast.makeText(getApplicationContext(), "Connection with BT was lost", Toast.LENGTH_LONG).show();
                    cleanup();

                    //Restarts the application
                    Intent i = getBaseContext().getPackageManager()
                            .getLaunchIntentForPackage( getBaseContext().getPackageName() );
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(i);
                }
            }
        }

    };



    void processBtControlMessage(int message) {
        if(message == BTService.BtMessageIn.BT_MESSAGE_IN_VOLTMETER_RANGE_0_TO_0_POINT_5.getValue()) {
            Log.d(TAG, "MenuActivity: processBtControlMessage: Going to voltmeter activity");
            Intent myIntent = new Intent(MenuActivity.this, VoltmeterActivity.class);
            MenuActivity.this.startActivity(myIntent);
        }
        if(message == BTService.BtMessageIn.BT_MESSAGE_IN_VOLTMETER_RANGE_0_TO_10.getValue()) {
            Log.d(TAG, "MenuActivity: processBtControlMessage: Going to voltmeter activity");
            Intent myIntent = new Intent(MenuActivity.this, Voltmeter0to10Activity.class);
            MenuActivity.this.startActivity(myIntent);
        }
        if(message == BTService.BtMessageIn.BT_MESSAGE_IN_VOLTMETER_RANGE_0_TO_50.getValue()) {
            Log.d(TAG, "MenuActivity: processBtControlMessage: Going to voltmeter activity");
            Intent myIntent = new Intent(MenuActivity.this, Voltmeter0to50Activity.class);
            MenuActivity.this.startActivity(myIntent);
        }
        else if(message == BTService.BtMessageIn.BT_MESSAGE_IN_AMPMETER.getValue()) {
            Log.d(TAG, "MenuActivity: processBtControlMessage: Going to ampmeter activity");
            Intent myIntent = new Intent(MenuActivity.this, AmpmeterActivity.class);
            MenuActivity.this.startActivity(myIntent);
        }
        else if(message == BTService.BtMessageIn.BT_MESSAGE_IN_OSCILLOSCOPE.getValue()) {
            Log.d(TAG, "MenuActivity: processBtControlMessage: Going to oscilloscope activity");
            Intent myIntent = new Intent(MenuActivity.this, OscilloscopeActivity.class);
            MenuActivity.this.startActivity(myIntent);
        }
    }



    void tryUnregisteringReceiver() {
        try {
            unregisterReceiver(btBroadcastReceiver);
        }
        catch(IllegalArgumentException e) {
            Log.e(TAG, "Receiver was already unregistered");
        }
        if(isBound){
            unbindService(mServiceConnection);
        }
        isBound = false;
    }



    void terminateBtConnection() {
        if(btService != null) {
            btService.terminateBtConnection();
        }
    }



    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RECEIVE_JSON);
        return intentFilter;
    }

    void bindServiceAndRegisterReceiver() {
        Intent gattServiceIntent = new Intent(MenuActivity.this, BTService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        registerReceiver(btBroadcastReceiver, makeGattUpdateIntentFilter());

        broadcastManager = LocalBroadcastManager.getInstance(this);
        broadcastManager.registerReceiver(btBroadcastReceiver, makeGattUpdateIntentFilter());
    }
}
