package com.fan.push;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.TextView;

import com.fan.push.client.IConnectStatusListener;
import com.fan.push.client.INewMessageListener;
import com.fan.push.client.PushClient;
import com.fan.push.message.Message;

/**
 * @Description: MainActivity
 * @Author: fan
 * @Date: 2020-10-19 21:19
 * @Modify:
 */
public class MainActivity extends AppCompatActivity {

    TextView pushContentView;
    TextView connectStatusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pushContentView = findViewById(R.id.content);
        pushContentView.setMovementMethod(ScrollingMovementMethod.getInstance());

        connectStatusView = findViewById(R.id.connect_status);

        PushClient.getInstance().setNewMessageListener(new INewMessageListener() {
            @Override
            public void onNewMessageReceived(Message message) {

                pushContentView.append("\n" + message.getContent());
            }
        });

        PushClient.getInstance().setConnectStatusListener(new IConnectStatusListener() {
            @Override
            public void connectSuccess() {
                // 连接成功

                MainActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        connectStatusView.setText("连接成功");
                    }
                });
            }

            @Override
            public void connectFail() {
                // 连接失败
                MainActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        connectStatusView.setText("连接失败");
                    }
                });
            }

            @Override
            public void connecting() {
                // 连接中
                MainActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        connectStatusView.setText("连接中...");
                    }
                });
            }
        });


        findViewById(R.id.connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                System.out.println("thread:" + Thread.currentThread().getName());
                                PushClient.getInstance().initBootStrap();
                                PushClient.getInstance().connect();
                            }
                        }
                ).start();
            }
        });
    }
}
