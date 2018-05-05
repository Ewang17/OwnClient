package com.example.ewang.helloworld;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.example.ewang.helloworld.adapter.MsgAdapter;
import com.example.ewang.helloworld.client.ClientThread;
import com.example.ewang.helloworld.client.Constants;
import com.example.ewang.helloworld.helper.HttpUtil;
import com.example.ewang.helloworld.helper.JsonHelper;
import com.example.ewang.helloworld.helper.MyApplication;
import com.example.ewang.helloworld.helper.ResponseWrapper;
import com.example.ewang.helloworld.model.Message;
import com.example.ewang.helloworld.model.Msg;
import com.example.ewang.helloworld.model.User;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.FormBody;
import okhttp3.RequestBody;

public class SessionActivity extends AppCompatActivity {

    private List<Msg> msgList = new ArrayList<>();

    private EditText editText;

    private Button sendBtn;

    private RecyclerView msgRecyclerView;

    private MsgAdapter adapter;

    private Handler readHandler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            msgList.add(new Msg((String) msg.obj, Msg.TYPE_RECEIVED));
            adapter.notifyItemInserted(msgList.size() - 1);
            msgRecyclerView.scrollToPosition(msgList.size() - 1);
            super.handleMessage(msg);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);
        long toUserId = getIntent().getLongExtra("toUserId", 0);

        User user = MyApplication.getCurrentUser();

        ClientThread clientThread = new ClientThread(user.getId(), readHandler);
        clientThread.start();


        editText = findViewById(R.id.input_text);
        sendBtn = findViewById(R.id.btn_send);
        msgRecyclerView = findViewById(R.id.msg_recycler_view);

        new Thread(new Runnable() {
            @Override
            public void run() {
                RequestBody requestBody = new FormBody.Builder()
                        .add("userId", String.valueOf(user.getId()))
                        .add("toUserId", String.valueOf(toUserId))
                        .build();
                String url = Constants.DefaultBasicUrl.getValue() + "/session/message/get";
                ResponseWrapper responseWrapper = HttpUtil.sendRequest(url, requestBody, SessionActivity.this);

                Map<String, Object> dataMap = responseWrapper.getData();
                List<Message> messageList = JsonHelper.decode(JsonHelper.encode(dataMap.get("messageList")), new TypeReference<List<Message>>() {
                });

                for (Message m : messageList) {
                    if (m.getUserId() == user.getId()) {
                        msgList.add(new Msg(m.getContent(), Msg.TYPE_SENT));
                    } else if (m.getToUserId() == user.getId()) {
                        msgList.add(new Msg(m.getContent(), Msg.TYPE_RECEIVED));
                    }
                }
                msgRecyclerView.scrollToPosition(msgList.size() - 1);

            }
        }).start();

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        msgRecyclerView.setLayoutManager(layoutManager);

        adapter = new MsgAdapter(msgList);
        msgRecyclerView.setAdapter(adapter);

        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String content = editText.getText().toString();
                if (content.isEmpty()) {
                    return;
                }

                String data = JsonHelper.encode(new Message(0, user.getId(), toUserId, content, 0, 0));
                android.os.Message message = android.os.Message.obtain();
                message.what = 0;
                message.obj = data;
                clientThread.writeHandler.sendMessage(message);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        RequestBody requestBody = new FormBody.Builder()
                                .add("content", content)
                                .add("userId", String.valueOf(user.getId()))
                                .add("toUserId", String.valueOf(toUserId))
                                .build();
                        String url = Constants.DefaultBasicUrl.getValue() + "/session/message/send";

                        ResponseWrapper responseWrapper = null;
                        do {
                            responseWrapper = HttpUtil.sendRequest(url, requestBody, SessionActivity.this);
                        } while (!responseWrapper.isSuccess());
                    }
                }).start();
                Msg msg = new Msg(content, Msg.TYPE_SENT);
                msgList.add(msg);
                adapter.notifyItemInserted(msgList.size() - 1);
                msgRecyclerView.scrollToPosition(msgList.size() - 1);
                editText.setText("");

            }
        });
    }

}
