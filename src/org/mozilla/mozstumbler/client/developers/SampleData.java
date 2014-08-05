package org.mozilla.mozstumbler.client.developers;

import org.mozilla.mozstumbler.client.models.Player;
import org.mozilla.mozstumbler.client.models.Score;

import java.util.ArrayList;
import java.util.Random;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class SampleData {

    public static ArrayList<Score> sampleDataForYourStats() {
        Score starScore = new Score(Score.ScoreType.STAR, 875);
        Score rainbowScore = new Score(Score.ScoreType.RAINBOW, 350);
        Score coinScore = new Score(Score.ScoreType.COIN, 700);

        ArrayList<Score> scores = new ArrayList<Score>();
        scores.add(starScore);
        scores.add(rainbowScore);
        scores.add(coinScore);

        return scores;
    }

    public static ArrayList<Player> sampleDataForYourRank() {
        Player player = new Player("Steamclock", 11550, 2964);

        ArrayList<Player> rank = new ArrayList<Player>();
        rank.add(player);

        return rank;
    }

    public static ArrayList<Player> sampleDataForTopTen() {
        ArrayList<Player> ranks = new ArrayList<Player>();

        for (int i = 1; i <= 10; i++) {
            Random rand = new Random();
            int newScore = rand.nextInt((300000 - 100000) + 1) + 100000;

            Player player = new Player("Nickname", newScore, i);
            ranks.add(player);
        }

        return ranks;
    }
}
