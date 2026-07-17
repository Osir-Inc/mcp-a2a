package com.osir.a2a.protocol;

public class TextPart extends Part {

    private String text;

    public TextPart() {}

    public TextPart(String text) {
        this.text = text;
    }

    @Override
    public String getType() { return "text"; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
