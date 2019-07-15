package com.google.android.gms.samples.vision.barcodereader;

public class Part {
    private byte index;
    private byte size;
    private String body;

    public Part(byte index, byte size, String body) {
        this.index = index;
        this.size = size;
        this.body = body;
    }

    public Part() {
    }

    public byte getIndex() {
        return index;
    }

    public void setIndex(byte index) {
        this.index = index;
    }

    public byte getSize() {
        return size;
    }

    public void setSize(byte size) {
        this.size = size;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

}
