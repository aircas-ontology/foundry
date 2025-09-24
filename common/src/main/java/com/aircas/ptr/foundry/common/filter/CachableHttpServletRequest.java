package com.aircas.ptr.foundry.common.filter;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class CachableHttpServletRequest extends HttpServletRequestWrapper {

    private byte[] body;
    private boolean bodyRead = false;

    public CachableHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        readBody(request);
    }

    private void readBody(HttpServletRequest request) throws IOException {
        ServletInputStream inputStream = request.getInputStream();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int byteRead;
        while ((byteRead = inputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, byteRead);
        }

        body = byteArrayOutputStream.toByteArray();
        bodyRead = true;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return new CachedServletInputStream(body);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body);
        return new BufferedReader(new InputStreamReader(byteArrayInputStream, getCharacterEncoding()));
    }

    public byte[] getBodyAsBytes() {
        return body;
    }

    public String getBodyAsString() {
        if (body == null) {
            return null;
        }
        return new String(body, StandardCharsets.UTF_8);
    }


    private static class CachedServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream inputStream;

        protected CachedServletInputStream(byte[] body) {
            this.inputStream = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read() throws IOException {
            return inputStream.read();
        }
    }
}
