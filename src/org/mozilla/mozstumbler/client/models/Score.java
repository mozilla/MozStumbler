package org.mozilla.mozstumbler.client.models;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class Score {
    public static enum ScoreType {
        STAR,
        RAINBOW,
        COIN
    }

    public static final int POINT_PER_STAR = 5;
    public static final int POINT_PER_RAINBOW = 50;
    public static final int POINT_PER_COIN = 100;

    private final String STAR_TITLE = "Stars";
    private final String RAINBOW_TITLE = "Rainbows";
    private final String COIN_TITLE = "Gold coins";
    private final String UNKNOWN_TITLE = "Unknown score type";

    private ScoreType scoreType;
    private int points;

    public Score(ScoreType scoreType, int points) {
        this.scoreType = scoreType;
        this.points = points;
    }

    public ScoreType getScoreType() {
        return scoreType;
    }

    public String getScoreTitle() {
        String scoreTitle = null;

        switch (scoreType) {
            case STAR:
                scoreTitle = STAR_TITLE;
                break;
            case RAINBOW:
                scoreTitle = RAINBOW_TITLE;
                break;
            case COIN:
                scoreTitle = COIN_TITLE;
                break;
            default:
                scoreTitle = UNKNOWN_TITLE;
                break;
        }

        return scoreTitle;
    }

    public int getScorePoints() {
        return points;
    }

    public void setScorePoints(int newPoints) {
        points = newPoints;
    }
}
