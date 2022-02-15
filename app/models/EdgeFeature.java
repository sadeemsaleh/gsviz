package models;

public class EdgeFeature {
    int[] color;
    int width;

    public EdgeFeature(int[] color, int width) {
        this.color = color;
        this.width = width;
    }

    public int[] getColor() {
        return color;
    }

    public void setColor(int[] color) {
        this.color = color;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }
}
