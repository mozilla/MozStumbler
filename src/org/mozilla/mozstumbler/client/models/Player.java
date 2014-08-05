package org.mozilla.mozstumbler.client.models;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class Player {
    private String playerName;
    private int playerPoints;
    private int playerRank;

    public Player(String playerName, int playerPoints, int playerRank) {
        this.playerName = playerName;
        this.playerPoints = playerPoints;
        this.playerRank = playerRank;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getPlayerPoints() {
        return playerPoints;
    }

    public int getPlayerRank() {
        return playerRank;
    }
}
