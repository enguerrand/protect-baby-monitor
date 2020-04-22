/**
 * This file is part of the Protect Baby Monitor.
 *
 * Protect Baby Monitor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Protect Baby Monitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Protect Baby Monitor. If not, see <http://www.gnu.org/licenses/>.
 */
package protect.babymonitor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;

import android.app.Activity;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.TextView;

public class MonitorActivity extends Activity {
    final static String TAG = "BabyMonitor";

    private NsdManager nsdManager;

    private NsdManager.RegistrationListener registrationListener;

    private ServerSocket currentSocket;

    private Object connectionToken;

    private int currentPort;

    private void serviceConnection(Socket socket) {
            runOnUiThread(new Runnable() {
                @Override
                public void run()
                {
                    final TextView statusText = (TextView) findViewById(R.id.textStatus);
                    statusText.setText(R.string.streaming);
                }
            });

            final int frequency = AudioCodecDefines.FREQUENCY;
            final int channelConfiguration = AudioCodecDefines.CHANNEL_CONFIGURATION_IN;
            final int audioEncoding = AudioCodecDefines.ENCODING;

            final int bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
            final AudioRecord audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    frequency,
                    channelConfiguration,
                    audioEncoding,
                    bufferSize
            );

            final int byteBufferSize = bufferSize*2;
            final byte[] buffer = new byte[byteBufferSize];

            try {
                audioRecord.startRecording();
                final OutputStream out = socket.getOutputStream();

                socket.setSendBufferSize(byteBufferSize);
                Log.d(TAG, "Socket send buffer size: " + socket.getSendBufferSize());

                while (socket.isConnected() && !Thread.currentThread().isInterrupted()) {
                    final int read = audioRecord.read(buffer, 0, bufferSize);
                    out.write(buffer, 0, read);
                }
            } catch (IOException e) {
                Log.e(TAG, "Connection failed", e);
            } finally {
                audioRecord.stop();
            }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Baby monitor start");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor);

        nsdManager = (NsdManager)this.getSystemService(Context.NSD_SERVICE);
        currentPort = 10000;
        currentSocket = null;
        final Object currentToken = new Object();
        connectionToken = currentToken;

        new Thread(new Runnable() {
            @Override
            public void run()
            {
                while(Objects.equals(connectionToken, currentToken)) {
                    try (ServerSocket serverSocket = new ServerSocket(currentPort)) {
                        currentSocket = serverSocket;
                        // Store the chosen port.
                        final int localPort = serverSocket.getLocalPort();

                        // Register the service so that parent devices can
                        // locate the child device
                        registerService(localPort);

                        // Wait for a parent to find us and connect
                        try (Socket socket = serverSocket.accept()) {
                            Log.i(TAG, "Connection from parent device received");

                            // We now have a client connection.
                            // Unregister so no other clients will
                            // attempt to connect
                            unregisterService();
                            serviceConnection(socket);
                        }
                    } catch(IOException e) {
                        // Just in case
                        currentPort++;
                        Log.e(TAG, "Failed to open server socket. Port increased to " + currentPort, e);
                    }
                }
            }
        }).start();

        final TextView addressText = (TextView) findViewById(R.id.address);

        // Use the application context to get WifiManager, to avoid leak before Android 5.1
        final WifiManager wifiManager =
                (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        final WifiInfo info = wifiManager.getConnectionInfo();
        final int address = info.getIpAddress();
        if(address != 0) {
            @SuppressWarnings("deprecation")
            final String ipAddress = Formatter.formatIpAddress(address);
            addressText.setText(ipAddress);
        } else {
            addressText.setText(R.string.wifiNotConnected);
        }

    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "Baby monitor stop");

        unregisterService();

        connectionToken = null;
        if(currentSocket != null) {
            try {
                currentSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close active socket on port "+currentPort);
            }
        }

        super.onDestroy();
    }

    private void registerService(final int port) {
        final NsdServiceInfo serviceInfo  = new NsdServiceInfo();
        serviceInfo.setServiceName("ProtectBabyMonitor");
        serviceInfo.setServiceType("_babymonitor._tcp.");
        serviceInfo.setPort(port);

        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo nsdServiceInfo) {
                // Save the service name.  Android may have changed it in order to
                // resolve a conflict, so update the name you initially requested
                // with the name Android actually used.
                final String serviceName = nsdServiceInfo.getServiceName();

                Log.i(TAG, "Service name: " + serviceName);

                MonitorActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run()
                    {
                        final TextView statusText = (TextView) findViewById(R.id.textStatus);
                        statusText.setText(R.string.waitingForParent);

                        final TextView serviceText = (TextView) findViewById(R.id.textService);
                        serviceText.setText(serviceName);

                        final TextView portText = (TextView) findViewById(R.id.port);
                        portText.setText(Integer.toString(port));
                    }
                });
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Registration failed!  Put debugging code here to determine why.
                Log.e(TAG, "Registration failed: " + errorCode);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo arg0) {
                // Service has been unregistered.  This only happens when you call
                // NsdManager.unregisterService() and pass in this listener.

                Log.i(TAG, "Unregistering service");
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Unregistration failed.  Put debugging code here to determine why.

                Log.e(TAG, "Unregistration failed: " + errorCode);
            }
        };

        nsdManager.registerService(
                serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
    }

    /**
     * Uhregistered the service and assigns the listener
     * to null.
     */
    private void unregisterService() {
        if(registrationListener != null) {
            Log.i(TAG, "Unregistering monitoring service");

            nsdManager.unregisterService(registrationListener);
            registrationListener = null;
        }
    }
}
