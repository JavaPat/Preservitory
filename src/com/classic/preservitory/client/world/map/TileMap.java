package com.classic.preservitory.client.world.map;

public class TileMap {

    private final int width;
    private final int height;
    private final int[][] tiles;

    public TileMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.tiles = new int[width][height];

        // default everything to grass (0)
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                tiles[x][y] = 0;
            }
        }
    }

    public int getTile(int x, int y) {
        return tiles[x][y];
    }

    public void setTile(int x, int y, int id) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        tiles[x][y] = id;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
}