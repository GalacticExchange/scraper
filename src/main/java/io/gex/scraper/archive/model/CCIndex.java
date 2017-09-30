package io.gex.scraper.archive.model;

import java.util.Objects;

public class CCIndex {
    private String name;
    private int creationYear;
    private int indexNum;

    public CCIndex(String name, int creationYear, int indexNum) {
        this.name = name;
        this.creationYear = creationYear;
        this.indexNum = indexNum;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCreationYear() {
        return creationYear;
    }

    public void setCreationYear(int creationYear) {
        this.creationYear = creationYear;
    }

    public int getIndexNum() {
        return indexNum;
    }

    public void setIndexNum(int indexNum) {
        this.indexNum = indexNum;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CCIndex ccIndex = (CCIndex) o;
        return Objects.equals(name, ccIndex.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
