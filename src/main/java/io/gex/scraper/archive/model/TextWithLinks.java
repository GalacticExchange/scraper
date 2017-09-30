package io.gex.scraper.archive.model;


import java.util.List;

public class TextWithLinks {
    private String text;
    private List<LinkWithOffset> links;

    public TextWithLinks() {
    }

    public TextWithLinks(String text, List<LinkWithOffset> links) {
        this.text = text;
        this.links = links;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<LinkWithOffset> getLinks() {
        return links;
    }

    public void setLinks(List<LinkWithOffset> links) {
        this.links = links;
    }
}
