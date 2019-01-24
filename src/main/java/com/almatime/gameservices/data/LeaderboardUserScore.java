package com.almatime.gameservices.data;

/**
 * Holds some data of {@link com.google.android.gms.games.leaderboard.LeaderboardScore}.
 *
 * @author Alexander Khrapunsky
 * @version 1.0.0, 30/10/2018.
 * @since 1.0.0
 */
public class LeaderboardUserScore {

    private String playerName;
    private String displayScore;
    private String displayRank;
    private long rank;
    private long rawScore;

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getDisplayScore() {
        return displayScore;
    }

    public void setDisplayScore(String displayScore) {
        this.displayScore = displayScore;
    }

    public String getDisplayRank() {
        return displayRank;
    }

    public void setDisplayRank(String displayRank) {
        this.displayRank = displayRank;
    }

    public long getRank() {
        return rank;
    }

    public void setRank(long rank) {
        this.rank = rank;
    }

    public long getRawScore() {
        return rawScore;
    }

    public void setRawScore(long rawScore) {
        this.rawScore = rawScore;
    }

}
