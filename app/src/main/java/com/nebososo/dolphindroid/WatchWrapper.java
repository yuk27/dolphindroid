package com.nebososo.dolphindroid;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.accessory.SA;
import com.samsung.android.sdk.accessory.SAAgent;
import com.samsung.android.sdk.accessory.SAMessage;
import com.samsung.android.sdk.accessory.SAPeerAgent;

import java.io.IOException;

//SSAgent Extends from Service, which can execute long lasting process that can run on second plane
//It can handle things as music, internet connections, I/O everything on second plane
public class WatchWrapper  extends SAAgent {

    private static final String TAG = "dolphindroid";
    private SAMessage mMessage = null;
    private SAPeerAgent mSAPeerAgent = null;
    private final IBinder mBinder = new LocalBinder(); //A binder is part of Services class, is used to be able to link to other services
    private final float[] localAccelerometer = new float[3];
    private int busy = 0; // Flag for defining if we are waiting for the watch response
    private static final int busy_limit = 3;

    private boolean processUnsupportedException(SsdkUnsupportedException e) {
        e.printStackTrace();
        int errType = e.getType();
        if (errType == SsdkUnsupportedException.VENDOR_NOT_SUPPORTED
                || errType == SsdkUnsupportedException.DEVICE_NOT_SUPPORTED) {
            /*
             * Your application can not use Samsung Accessory SDK. You application should work smoothly
             * without using this SDK, or you may want to notify user and close your app gracefully (release
             * resources, stop Service threads, close UI thread, etc.)
             */
            stopSelf();
        } else if (errType == SsdkUnsupportedException.LIBRARY_NOT_INSTALLED) {
            Log.e(TAG, "You need to install Samsung Accessory SDK to use this application.");
        } else if (errType == SsdkUnsupportedException.LIBRARY_UPDATE_IS_REQUIRED) {
            Log.e(TAG, "You need to update Samsung Accessory SDK to use this application.");
        } else if (errType == SsdkUnsupportedException.LIBRARY_UPDATE_IS_RECOMMENDED) {
            Log.e(TAG, "We recommend that you update your Samsung Accessory SDK before using this application.");
            return false;
        }
        return true;
    }

    @Override
    public void onDestroy() {
        mSAPeerAgent = null;
        super.onDestroy();
    }

    @Override
    protected void onFindPeerAgentsResponse(SAPeerAgent[] peerAgents, int result) {
        if ((result == SAAgent.PEER_AGENT_FOUND) && (peerAgents != null)) {
            Toast.makeText(getApplicationContext(), "PEERAGENT_FOUND", Toast.LENGTH_LONG).show();
            for(SAPeerAgent peerAgent:peerAgents) {
                mSAPeerAgent = peerAgent;
            }
        } else if (result == SAAgent.FINDPEER_DEVICE_NOT_CONNECTED) {
            Toast.makeText(getApplicationContext(), "FINDPEER_DEVICE_NOT_CONNECTED", Toast.LENGTH_LONG).show();
        } else if (result == SAAgent.FINDPEER_SERVICE_NOT_FOUND) {
            Toast.makeText(getApplicationContext(), "FINDPEER_SERVICE_NOT_FOUND", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getApplicationContext(), "No Peers founds", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    public class LocalBinder extends Binder {
        public WatchWrapper getService() {
            return WatchWrapper.this;
        } // Binds to this service
    }

    public WatchWrapper() {
        super(TAG); // Calls the constructor of SSAgent
    }

    @Override
    public void onCreate() {
        super.onCreate(); // Call SAAgent creation
        SA mAccessory = new SA();
        try {
            mAccessory.initialize(this);
        } catch (SsdkUnsupportedException e) {
            // try to handle SsdkUnsupportedException
            if (processUnsupportedException(e) == true) {
                return;
            }
        } catch (Exception e1) {
            e1.printStackTrace();
            /*
             * Your application can not use Samsung Accessory SDK. Your application should work smoothly
             * without using this SDK, or you may want to notify user and close your application gracefully
             * (release resources, stop Service threads, close UI thread, etc.)
             */
            stopSelf();
        }

        mMessage = new SAMessage(this) {

            @Override
            protected void onSent(SAPeerAgent peerAgent, int id) {

                /*Log.d(TAG, "onSent(), id: " + id + ", ToAgent: " + peerAgent.getPeerId());
                String val = "" + id + " SUCCESS ";
                displayToast("ACK Received: " + val, Toast.LENGTH_SHORT);*/
            }

            @Override
            protected void onError(SAPeerAgent peerAgent, int id, int errorCode) {

                Log.d(TAG, "onError(), id: " + id + ", ToAgent: " + peerAgent.getPeerId() + ", errorCode: " + errorCode);
                String result = null;
                switch (errorCode) {
                    case ERROR_PEER_AGENT_UNREACHABLE:
                        result = " FAILURE" + "[ " + errorCode + " ] : PEER_AGENT_UNREACHABLE ";
                        break;
                    case ERROR_PEER_AGENT_NO_RESPONSE:
                        result = " FAILURE" + "[ " + errorCode + " ] : PEER_AGENT_NO_RESPONSE ";
                        break;
                    case ERROR_PEER_AGENT_NOT_SUPPORTED:
                        result = " FAILURE" + "[ " + errorCode + " ] : ERROR_PEER_AGENT_NOT_SUPPORTED ";
                        break;
                    case ERROR_PEER_SERVICE_NOT_SUPPORTED:
                        result = " FAILURE" + "[ " + errorCode + " ] : ERROR_PEER_SERVICE_NOT_SUPPORTED ";
                        break;
                    case ERROR_SERVICE_NOT_SUPPORTED:
                        result = " FAILURE" + "[ " + errorCode + " ] : ERROR_SERVICE_NOT_SUPPORTED ";
                        break;
                    case ERROR_UNKNOWN:
                        result = " FAILURE" + "[ " + errorCode + " ] : UNKNOWN ";
                        break;
                }
                String val = "" + id + result;
                //displayToast("NAK Received: " + val, Toast.LENGTH_SHORT);
                Log.e(TAG, val);
                //ConsumerActivity.updateButtonState(false);
            }

            @Override
            protected void onReceive(SAPeerAgent peerAgent, byte[] message) {
                if(busy > 0){
                    busy--;
                } // release the connection
                String dataVal = new String(message);

                try {
                    String[] separated = dataVal.split(",");
                    for(int i = 0; i < 3; i++){
                        String s = separated[i].substring(0, Math.min(separated[i].length(), 8));
                        localAccelerometer[i] = Float.parseFloat(s);
                    }
                }catch (Exception e){
                    Log.e(TAG, "Error getting value to float: " + dataVal);
                    localAccelerometer[0] = 0;
                    localAccelerometer[1] = 0;
                    localAccelerometer[2] = 0;
                }
            }
        };
    }

    public float[] GetAccelerometer(){
        float[] res = null;
        if(mSAPeerAgent != null){
            res = localAccelerometer;
            if(busy < busy_limit){
                sendData("0");
                busy++;
            }
        }
        return res;
    }

    public int sendData(String message) {
        int tid;

        if(mSAPeerAgent == null) {
            Log.e(TAG,"Try to find PeerAgent!");
            return -1;
        }
        if (mMessage != null) {
            try {
                tid = mMessage.send(mSAPeerAgent, message.getBytes());
                //addMessage("Sent: ", message);
                return tid;
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                return -1;
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                return -1;
            }
        }
        return -1;
    }

    public void findPeers() {
        findPeerAgents();
    }

}

