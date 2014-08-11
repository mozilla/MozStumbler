package org.mozilla.mozstumbler.client.models;

import org.mozilla.mozstumbler.service.Prefs;

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

    private int rainbowCountToday;
    private final int numOfRainbowsRequiredForCoin = 2;

    private Prefs preferences;

    private UserScoreUpdatedListener userScoreUpdatedListener;
    private CoinRewardedListener coinRewardedListener;

    public User(String userName, Prefs prefs) {
        super(userName, 0, 0);

        preferences = prefs;

        if (preferences.getUserTotalPoints() < 0) {
            resetScoreForToday();
        } else {
            int starScore = preferences.getStarScoreToday();
            int rainbowScore = preferences.getRainbowScoreToday();
            int coinScore = preferences.getCoinScoreToday();
            int rainbowCount = preferences.getRainbowCountToday();
            int totalPoints = preferences.getUserTotalPoints();

            starScoreToday = new Score(Score.ScoreType.STAR, starScore);
            rainbowScoreToday = new Score(Score.ScoreType.RAINBOW, rainbowScore);
            coinScoreToday = new Score(Score.ScoreType.COIN, coinScore);
            rainbowCountToday = rainbowCount;
            playerPoints = totalPoints;
        }
    }

    public void incrementStarScore() {
        starScoreToday.setScorePoints(starScoreToday.getScorePoints() + Score.POINT_PER_STAR);
        playerPoints += getStarScoreToday();

        if (userScoreUpdatedListener != null) {
            userScoreUpdatedListener.userScoreUpdated(this);
        }

        preferences.saveStarScoreToday(getStarScoreToday());
        saveTotalUserPoints();
    }

    public void incrementRainbowScore() {
        rainbowScoreToday.setScorePoints(rainbowScoreToday.getScorePoints() + Score.POINT_PER_RAINBOW);
        playerPoints += getRainbowScoreToday();

        if (userScoreUpdatedListener != null) {
            userScoreUpdatedListener.userScoreUpdated(this);
        }

        rainbowCountToday++;
        preferences.saveRainbowCountToday(rainbowCountToday);

        if (rainbowCountToday == numOfRainbowsRequiredForCoin) {
            incrementCoinScore();
            rainbowCountToday = 0;
        }

        preferences.saveRainbowScoreToday(getRainbowScoreToday());
        saveTotalUserPoints();
    }

    private void incrementCoinScore() {
        coinScoreToday.setScorePoints(coinScoreToday.getScorePoints() + Score.POINT_PER_COIN);
        playerPoints += getCoinScoreToday();

        if (userScoreUpdatedListener != null) {
            userScoreUpdatedListener.userScoreUpdated(this);
            coinRewardedListener.coinRewarded();
        }

        preferences.saveCoinScoreToday(getCoinScoreToday());
        saveTotalUserPoints();
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

        rainbowCountToday = 0;

        preferences.saveStarScoreToday(0);
        preferences.saveRainbowScoreToday(0);
        preferences.saveCoinScoreToday(0);
        preferences.saveRainbowCountToday(0);
        preferences.saveUserTotalPoints(0);
    }

    public void setUserScoreUpdatedListener(UserScoreUpdatedListener userScoreUpdatedListener) {
        this.userScoreUpdatedListener = userScoreUpdatedListener;
    }

    public void setCoinRewardedListener(CoinRewardedListener coinRewardedListener) {
        this.coinRewardedListener = coinRewardedListener;
    }

    private void saveTotalUserPoints() {
        preferences.saveUserTotalPoints(playerPoints);
    }
}
