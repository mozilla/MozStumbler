package org.mozilla.mozstumbler.client.models;

/**
 * Created by JeremyChiang on 2014-08-07.
 */
public class User extends Player {

    public interface UserScoreUpdatedListener {
        public void userScoreUpdated(User user);
    }

    public interface CoinRewardedListener {
        public void coinRewarded();
    }

    private Score starScoreToday;
    private Score rainbowScoreToday;
    private Score coinScoreToday;

    private int rainbowCount;
    private final int numOfRainbowsRequiredForCoin = 2;

    private UserScoreUpdatedListener userScoreUpdatedListener;
    private CoinRewardedListener coinRewardedListener;

    public User(String userName) {
        super(userName, 0, 0);

        resetScoreForToday();
    }

    public void incrementStarScore() {
        starScoreToday.setScorePoints(starScoreToday.getScorePoints() + Score.POINT_PER_STAR);

        if (userScoreUpdatedListener != null) {
            userScoreUpdatedListener.userScoreUpdated(this);
        }
    }

    public void incrementRainbowScore() {
        rainbowScoreToday.setScorePoints(rainbowScoreToday.getScorePoints() + Score.POINT_PER_RAINBOW);

        if (userScoreUpdatedListener != null) {
            userScoreUpdatedListener.userScoreUpdated(this);
        }

        rainbowCount++;

        if (rainbowCount == numOfRainbowsRequiredForCoin) {
            incrementCoinScore();
            rainbowCount = 0;
        }
    }

    private void incrementCoinScore() {
        coinScoreToday.setScorePoints(coinScoreToday.getScorePoints() + Score.POINT_PER_COIN);

        if (userScoreUpdatedListener != null) {
            userScoreUpdatedListener.userScoreUpdated(this);
            coinRewardedListener.coinRewarded();
        }
    }

    public int getStarScoreToday() {
        return starScoreToday.getScorePoints();
    }

    public int getRainbowScoreToday() {
        return rainbowScoreToday.getScorePoints();
    }

    public int getCoinScoreToday() {
        return coinScoreToday.getScorePoints();
    }

    public void resetScoreForToday() {
        starScoreToday = new Score(Score.ScoreType.STAR, 0);
        rainbowScoreToday = new Score(Score.ScoreType.RAINBOW, 0);
        coinScoreToday = new Score(Score.ScoreType.COIN, 0);

        rainbowCount = 0;
    }

    public void setUserScoreUpdatedListener(UserScoreUpdatedListener userScoreUpdatedListener) {
        this.userScoreUpdatedListener = userScoreUpdatedListener;
    }

    public void setCoinRewardedListener(CoinRewardedListener coinRewardedListener) {
        this.coinRewardedListener = coinRewardedListener;
    }
}
