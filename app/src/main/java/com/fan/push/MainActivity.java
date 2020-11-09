package com.fan.push;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import com.fan.push.client.PushClient;

import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

public class MainActivity extends AppCompatActivity {
    PushClient pushClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        final EditText editText = findViewById(R.id.tv);


        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                if (pushClient != null && pushClient.channel != null)
//                    pushClient.channel.writeAndFlush(Unpooled.wrappedBuffer(editText.getText().toString().getBytes(CharsetUtil.UTF_8)));

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
//                                pushClient = new PushClient();
//                                pushClient.connect();
                            }
                        }
                ).start();
            }
        });
    }
}
