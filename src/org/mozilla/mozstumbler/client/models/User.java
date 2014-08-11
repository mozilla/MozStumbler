package org.mozilla.mozstumbler.client.models;

import org.mozilla.mozstumbler.service.Prefs;

import java.util.ArrayList;

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
            preferences.incrementStarScoreOverall(0);
            preferences.incrementRainbowScoreOverall(0);
            preferences.incrementCoinScoreOverall(0);

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
        preferences.incrementStarScoreOverall(Score.POINT_PER_STAR);
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
        preferences.incrementRainbowScoreOverall(Score.POINT_PER_RAINBOW);
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
        preferences.incrementCoinScoreOverall(Score.POINT_PER_COIN);
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
    }

    public ArrayList<Score> getStats() {
        ArrayList<Score> stats = new ArrayList<Score>();

        Score overallStar = new Score(Score.ScoreType.STAR, preferences.getStarScoreOverall());
        Score overallRainbow = new Score(Score.ScoreType.RAINBOW, preferences.getRainbowScoreOverall());
        Score overallCoin = new Score(Score.ScoreType.COIN, preferences.getCoinScoreOverall());

        stats.add(overallStar);
        stats.add(overallRainbow);
        stats.add(overallCoin);

        return stats;
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
