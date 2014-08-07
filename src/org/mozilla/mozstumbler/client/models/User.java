package org.mozilla.mozstumbler.client.models;

/**
 * Created by JeremyChiang on 2014-08-07.
 */
public class User extends Player {

    public interface UserScoreUpdatedListener {
        public void userScoreUpdated(User user);
    }

    private Score starScore;
    private Score rainbowScore;
    private Score coinScore;

    private UserScoreUpdatedListener userScoreUpdatedListener;

    public User(String userName) {
        super(userName, 0, 0);

        starScore = new Score(Score.ScoreType.STAR, 0);
        rainbowScore = new Score(Score.ScoreType.RAINBOW, 0);
        coinScore = new Score(Score.ScoreType.COIN, 0);
    }

    public void incrementStarScore() {
        starScore.setScorePoints(starScore.getScorePoints() + Score.POINT_PER_STAR);

        if (userScoreUpdatedListener != null) {
            userScoreUpdatedListener.userScoreUpdated(this);
        }
    }

    public void incrementRainbowScore() {
        rainbowScore.setScorePoints(rainbowScore.getScorePoints() + Score.POINT_PER_RAINBOW);

        if (userScoreUpdatedListener != null) {
            userScoreUpdatedListener.userScoreUpdated(this);
        }
    }

    public void incrementCoinScore() {
        coinScore.setScorePoints(coinScore.getScorePoints() + Score.POINT_PER_COIN);

        if (userScoreUpdatedListener != null) {
            userScoreUpdatedListener.userScoreUpdated(this);
        }
    }

    public int getStarScore() {
        return starScore.getScorePoints();
    }

    public int getRainbowScore() {
        return rainbowScore.getScorePoints();
    }

    public int getCoinScore() {
        return coinScore.getScorePoints();
    }

    public void setUserScoreUpdatedListener(UserScoreUpdatedListener userScoreUpdatedListener) {
        this.userScoreUpdatedListener = userScoreUpdatedListener;
    }
}
