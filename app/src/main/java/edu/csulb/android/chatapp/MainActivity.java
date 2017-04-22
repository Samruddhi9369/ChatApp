package edu.csulb.android.chatapp;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;


import edu.csulb.android.chatapp.service.BluetoothChatService;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static org.apache.commons.io.IOUtils.toByteArray;

public class MainActivity extends AppCompatActivity {

    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int AUDIO_READ = 6;
    public static final int AUDIO_WRITE = 7;

    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    private ListView lvMainChat;
    private EditText editTextMain;
    private Button btnSend;

    private String connectedDeviceName = null;
    private ArrayAdapter<String> chatArrayAdapter;

    private StringBuffer outStringBuffer;
    private BluetoothAdapter bluetoothAdapter = null;
    private BluetoothChatService chatService = null;

    private MediaRecorder mediaRecorder;
    Button buttonStart, buttonStop, buttonPlayLastRecordAudio, btnSendAudio, btnSendImage;
    String AudioSavePathInDevice = null;
    Random random ;
    ImageView imageView;
    String recaudio, recimage="";
    static boolean audiorecd=false, imagerecd=false;
    String RandomAudioFileName = "ABCDEFGHIJKLMNOP";
    public static final int RequestPermissionCode = 1;
    MediaPlayer mediaPlayer ;
    private final int CHOOSE_FILE_RESULT_CODE=20;

    private Handler handler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to,
                                    connectedDeviceName));
                            chatArrayAdapter.clear();
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;

                    String writeMessage = new String(writeBuf);
                    chatArrayAdapter.add("Me:  " + writeMessage);
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    //String readMessageAudio = Base64.encodeToString(, Base64.DEFAULT);
                    if(readMessage.contains("AUDIOEND")) {
                        recaudio = recaudio + readMessage;
                        audiorecd = false;
                        recaudio = recaudio.substring(5);
                        recaudio = recaudio.replaceFirst("AUDIOEND", "");
                        //String readMessageAudio = Base64.encodeToString(recaudio., Base64.DEFAULT);
                        byte[] b = Base64.decode(recaudio,Base64.DEFAULT);
                        Log.d("Audio Recd", recaudio);
                        String receiveFile = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" +
                                "AUD"+ CreateRandomAudioFileName(5) + "AudioRecording.aac";
                        FileOutputStream fos = null;
                        try {
                            fos = new FileOutputStream(new File(receiveFile));

                            //byte[] b = new byte[recaudio.getBytes().length];
                            fos.write(b);
                            fos.close();
                            //Log.e("SENDAUDIO",readMessage);
                            playAudio(receiveFile);
                            //Toast.makeText(MainActivity.this, "Recording Playing", Toast.LENGTH_LONG).show();
                            recaudio="";
                            break;
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if(audiorecd ==  true){
                        //Log.d("Audio Recd",recaudio);
                        recaudio = recaudio+readMessage;
                    }
                    if(readMessage.length()>5 && readMessage.substring(0,5).matches("AUDIO")){

                        recaudio = recaudio+readMessage;
                        audiorecd=true;
                    }
                    //IMAGE
                    if(readMessage.contains("IMAGEEND")) {
                        recimage = recimage + readMessage;
                        imagerecd = false;
                        recimage = recimage.substring(5);
                        recimage = recimage.replaceAll("IMAGEEND", "");
                        //recimage=recimage.replaceAll("\n","");
                        Log.d("Image Recd", recimage);
                        Bitmap image = StringToBitMap(recimage);
                        imageView.setImageBitmap(image);
                        imageView.setVisibility(View.VISIBLE);
                        recimage="";
                        break;
                    }
                    if(imagerecd ==  true){
                        //Log.d("Image Recd",recimage);
                        recimage = recimage+readMessage;
                    }
                    if(readMessage.length()>5 && readMessage.substring(0,5).matches("IMAGE")){

                        recimage = recimage+readMessage;
                        imagerecd=true;
                    }
                    chatArrayAdapter.add(connectedDeviceName + ":  " + readMessage);
                    break;
                case MESSAGE_DEVICE_NAME:

                    connectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(),
                            "Connected to " + connectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                    break;
            }
            return false;
        }
    });

    private void playAudio(String receiveFile) {
        MediaPlayer mediaPlayer = new MediaPlayer();
        mediaPlayer.reset();
        try {
            mediaPlayer.setDataSource(receiveFile);
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mediaPlayer.start();
        Toast.makeText(MainActivity.this, "Recording Playing", Toast.LENGTH_LONG).show();
    }

    public String BitMapToString(Bitmap bitmap){
        ByteArrayOutputStream baos=new  ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG,100, baos);
        byte [] b=baos.toByteArray();
        String temp=Base64.encodeToString(b, Base64.DEFAULT);
        return temp;
    }

    public Bitmap StringToBitMap(String encodedString){
        try {
            byte [] encodeByte=Base64.decode(encodedString,Base64.DEFAULT);
            Bitmap bitmap= BitmapFactory.decodeByteArray(encodeByte, 0, encodeByte.length);
            return bitmap;
        } catch(Exception e) {
            e.getMessage();
            return null;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonStart = (Button) findViewById(R.id.btnStartRecord);
        buttonStop = (Button) findViewById(R.id.btnStopRecord);
        buttonPlayLastRecordAudio = (Button) findViewById(R.id.btnPlayRecord);
        btnSendAudio = (Button) findViewById(R.id.btnSendRecord);
        btnSendImage = (Button) findViewById(R.id.btnSendImage);
        imageView = (ImageView) findViewById(R.id.imageView);
        buttonStop.setEnabled(false);
        buttonPlayLastRecordAudio.setEnabled(false);

        random = new Random();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        getWidgetReferences();
        bindEventHandler();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording();
            }
        });

        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
            }
        });

        buttonPlayLastRecordAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) throws IllegalArgumentException,
                    SecurityException, IllegalStateException {

                buttonStop.setEnabled(false);
                buttonStart.setEnabled(false);
                btnSendAudio.setEnabled(true);

                mediaPlayer = new MediaPlayer();
                mediaPlayer.reset();
                try {
                    mediaPlayer.setDataSource(AudioSavePathInDevice);
                    mediaPlayer.prepare();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                mediaPlayer.start();
                Toast.makeText(MainActivity.this, "Recording Playing", Toast.LENGTH_LONG).show();
            }
        });

        btnSendAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendAudio(AudioSavePathInDevice);
                /*byte[] bytes={};
                File aud = new File(AudioSavePathInDevice);
                if(aud!=null)
                {
                    try {
                        BufferedInputStream buf = new BufferedInputStream(new FileInputStream(aud));
                        buf.read(bytes,0,bytes.length);
                        buf.close();
                        bytes=toByteArray(new FileInputStream(aud));
                        String audio = Base64.encodeToString(bytes,Base64.DEFAULT);
                        audio = "AUDIO"+audio+"AUDIOEND";
                        sendMessage(audio);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }*/
            }
        });

        btnSendImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
            }
        });
    }

    private void stopRecording() {
        mediaRecorder.stop();
        buttonStop.setEnabled(false);
        buttonPlayLastRecordAudio.setEnabled(true);
        buttonStart.setEnabled(true);
        btnSendAudio.setEnabled(true);

        Toast.makeText(MainActivity.this, "Recording Completed", Toast.LENGTH_LONG).show();
    }

    private void getWidgetReferences() {
        lvMainChat = (ListView) findViewById(R.id.lvMainChat);
        editTextMain = (EditText) findViewById(R.id.editTextMain);
        btnSend = (Button) findViewById(R.id.btnSend);
    }

    private void bindEventHandler() {
        editTextMain.setOnEditorActionListener(mWriteListener);

        btnSend.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String message = editTextMain.getText().toString();
                sendMessage(message);
            }
        });

        btnSendAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendAudio(AudioSavePathInDevice);
                /*byte[] bytes={};
                File aud = new File(AudioSavePathInDevice);
                if(aud!=null)
                {
                    try {
                        BufferedInputStream buf = new BufferedInputStream(new FileInputStream(aud));
                        buf.read(bytes,0,bytes.length);
                        buf.close();
                        bytes=toByteArray(new FileInputStream(aud));
                        String audio = Base64.encodeToString(bytes,Base64.DEFAULT);
                        audio = "AUDIO"+audio+"AUDIOEND";
                        sendMessage(audio);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }*/
            }
        });

        btnSendImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
            }
        });
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case CHOOSE_FILE_RESULT_CODE:
                Uri uri = data.getData();
                sendImage(uri);
                break;
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    setupChat();
                } else {
                    Toast.makeText(this, R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    private void connectDevice(Intent data, boolean secure) {
        String address = data.getExtras().getString(
                DeviceListActivity.DEVICE_ADDRESS);
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        chatService.connect(device, secure);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent = null;
        switch (item.getItemId()) {
            case R.id.secure_connect_scan:
                serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            case R.id.insecure_connect_scan:
                serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
                return true;
            case R.id.discoverable:
                ensureDiscoverable();
                return true;
        }
        return false;
    }

    private void ensureDiscoverable() {
        if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(
                    BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    private void sendMessage(String message) {
        if (chatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        if (message.length() > 0) {
            byte[] send = message.getBytes();
            chatService.write(send);

            outStringBuffer.setLength(0);
            editTextMain.setText(outStringBuffer);
        }
    }

    private void sendImage(Uri uri){
        Bitmap bitmap = null;
        try {
            bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
            String imageString = BitMapToString(bitmap);
            imageString = "IMAGE"+imageString+"IMAGEEND";
            Log.d("Imge send ", imageString);
            sendMessage(imageString);
            outStringBuffer.setLength(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendAudio(String AudioSavePathInDevice) {
        if (chatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        if (AudioSavePathInDevice != null) {
            byte[] bytes={};
            File aud = new File(AudioSavePathInDevice);
            if(aud!=null)
            {
                try {
                    BufferedInputStream buf = new BufferedInputStream(new FileInputStream(aud));
                    buf.read(bytes,0,bytes.length);
                    buf.close();
                    bytes=toByteArray(new FileInputStream(aud));
                    String audio = Base64.encodeToString(bytes,Base64.DEFAULT);
                    audio = "AUDIO"+audio+"AUDIOEND";
                    sendMessage(audio);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private TextView.OnEditorActionListener mWriteListener = new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId,
                                      KeyEvent event) {
            if (actionId == EditorInfo.IME_NULL
                    && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            return true;
        }
    };

    private final void setStatus(int resId) {
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setSubtitle(resId);
    }

    private final void setStatus(CharSequence subTitle) {
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setSubtitle(subTitle);
    }

    private void setupChat() {
        chatArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        lvMainChat.setAdapter(chatArrayAdapter);

        chatService = new BluetoothChatService(handler);

        outStringBuffer = new StringBuffer("");
    }

    @Override
    public void onStart() {
        super.onStart();

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            if (chatService == null)
                setupChat();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

        if (chatService != null) {
            if (chatService.getState() == BluetoothChatService.STATE_NONE) {
                chatService.start();
            }
        }
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (chatService != null)
            chatService.stop();
    }

    public boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(getApplicationContext(),
                WRITE_EXTERNAL_STORAGE);
        int result1 = ContextCompat.checkSelfPermission(getApplicationContext(),
                RECORD_AUDIO);
        return result == PackageManager.PERMISSION_GRANTED &&
                result1 == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(MainActivity.this, new
                String[]{WRITE_EXTERNAL_STORAGE, RECORD_AUDIO}, RequestPermissionCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case RequestPermissionCode:
                if (grantResults.length> 0) {
                    boolean StoragePermission = grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED;
                    boolean RecordPermission = grantResults[1] ==
                            PackageManager.PERMISSION_GRANTED;

                    if (StoragePermission && RecordPermission) {
                        Toast.makeText(MainActivity.this, "Permission Granted",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this,"Permission Denied",Toast.LENGTH_LONG).show();
                    }
                }
                break;
        }
    }

    public void MediaRecorderReady(){
        mediaRecorder=new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
        mediaRecorder.setOutputFile(AudioSavePathInDevice);
    }

    public String CreateRandomAudioFileName(int string){
        StringBuilder stringBuilder = new StringBuilder( string );
        int i = 0 ;
        while(i < string ) {
            stringBuilder.append(RandomAudioFileName.
                    charAt(random.nextInt(RandomAudioFileName.length())));

            i++ ;
        }
        return stringBuilder.toString();
    }

    public void startRecording(){
        if(checkPermission()) {

            AudioSavePathInDevice =
                    Environment.getExternalStorageDirectory().getAbsolutePath() + "/" +
                            "AUD"+ CreateRandomAudioFileName(5) + "AudioRecording.aac";

            MediaRecorderReady();

            try {
                mediaRecorder.prepare();
                mediaRecorder.start();
            } catch (IllegalStateException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            buttonStart.setEnabled(false);
            buttonStop.setEnabled(true);
            buttonPlayLastRecordAudio.setEnabled(false);
            btnSendAudio.setEnabled(false);

            Toast.makeText(MainActivity.this, "Recording started",
                    Toast.LENGTH_LONG).show();
        } else {
            requestPermission();
        }

    }
}
