package com.example.roleplaychat.data.remote.sse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.roleplaychat.domain.model.AppErrorCode;
import com.example.roleplaychat.domain.repository.AiStreamListener;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SseClientTest {

    @Test
    public void doneCompletesOnce_andLateFailureIsIgnored() throws Exception {
        Call call = mock(Call.class);
        when(call.isCanceled()).thenReturn(false);
        AiStreamListener listener = mock(AiStreamListener.class);
        ArgumentCaptor<Callback> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
        SseClient client = new SseClient("request-1", call, listener);
        client.start();
        verify(call).enqueue(callbackCaptor.capture());
        Callback callback = callbackCaptor.getValue();
        String body = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"hello\"}}]}\n\n"
                + "data: [DONE]\n\n";
        Response response = new Response.Builder()
                .request(new Request.Builder().url("https://example.test/").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(body, MediaType.get("text/event-stream")))
                .build();

        callback.onResponse(call, response);
        callback.onFailure(call, new IOException("late failure"));

        verify(listener).onTextDelta("request-1", "hello");
        verify(listener, times(1)).onCompleted("request-1", "hello");
        verify(listener, never()).onFailed(any(), any(), any());
    }

    @Test
    public void cancelDeliversExactlyOneTerminalFailure() throws Exception {
        Call call = mock(Call.class);
        AiStreamListener listener = mock(AiStreamListener.class);
        ArgumentCaptor<Callback> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
        SseClient client = new SseClient("request-1", call, listener);
        client.start();
        verify(call).enqueue(callbackCaptor.capture());

        client.cancel();
        callbackCaptor.getValue().onFailure(call, new IOException("cancelled"));

        verify(call).cancel();
        verify(listener, times(1)).onFailed(
                "request-1", AppErrorCode.CANCELLED_BY_USER, null);
        verify(listener, never()).onCompleted(any(), any());
    }
}
