package com.almatime.gameservices;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.almatime.gameservices.data.LeaderboardUserScore;
import com.almatime.utils.Log;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.games.AchievementsClient;
import com.google.android.gms.games.AnnotatedData;
import com.google.android.gms.games.EventsClient;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.LeaderboardsClient;
import com.google.android.gms.games.PlayersClient;
import com.google.android.gms.games.leaderboard.LeaderboardScore;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Iterator;

/**
 * A <b>Singleton</b> class provides Google authentication, Google Play services API.
 * <p>IMPORTANT: implement {@link GameServicesListener}, and the following functions must be called
 * for full cycle initialization:</p>
 *
 * 1) {@link #init(Activity, EnumSet)} call it from onCreate(..)
 * 2) {@link #signInSilently()} call it from onResume() or from onStart() method.
 * 3) {@link #onActivityResult(int, int, Intent)} call it from onActivityResult(..)
 * 4) {@link #destroy()} call it from destroy()
 *
 * Effectively stores achievements with statuses and leaderboards scores in preferences on
 * purpose of reducing api calls and handling connection losing.
 *
 * When using <b>achievements</b> assign a required ids before calling {@link #init(Activity, EnumSet)}:
 * {@link #setUnlockAchievementIds(String[])}
 * {@link #setIncrementAchievementIds(String[])}
 *
 * When using <b>leaderboards</b> assign an ids before calling {@link #init(Activity, EnumSet)}:
 * {@link #setLeaderboardIds(String[])}
 *
 * <i><b>Add to proguard-rules.pro the following:</b>
 * -keep class PACKAGE_OF_THIS_CLASS.** {
 *     public protected private *;
 * }</i>
 *
 * @author Alexander Khrapunsky
 * @version 1.0.0, 30/10/2018.
 * @since 1.0.0
 */
public class GameServices {

    // tag for debug logging
    private final String TAG = "gameServices";

    // request codes we use when invoking an external activity
    private final int RC_UNUSED = 5001;
    private final int RC_SIGN_IN = 9001;
    private final int RC_ACHIEVEMENT_UI = 9003;
    private final int RC_LEADERBOARD_UI = 9004;

    private static GameServices instance = new GameServices();

    private Activity activity;
    private Context appContext;

    // notify listeners
    private GameServicesListener gameServicesListener;
    private LeaderboardServicesListener leaderboardServicesListener;

    // Client used to sign in with Google APIs
    private GoogleSignInClient googleSignInClient;

    // Client variables
    private AchievementsClient achievementsClient;
    private LeaderboardsClient leaderboardsClient;
    private EventsClient eventsClient;
    private PlayersClient playersClient;

    private EnumSet<SetClient> setClientsFlags;

    private String[] unlockAchievementIds;
    private String[] incrementAchievementIds;
    private String[] leaderboardIds;

    private Runnable taskOnSignInSuccess;

    /**
     * Flags to determine which Google Clients to include in initialization.
     */
    public enum SetClient {
        ACHIEVEMENTS,
        EVENTS,
        LEADERBOARD,
        MULTIPLAYER, // turn based
        PLAYERS;

        @Override
        public String toString() {
            return name().charAt(0) + name().substring(1).toLowerCase();
        }
    }

    /**
     * The notified gameServicesListener must be implemented in your Activity!
     */
    public interface GameServicesListener {

        void onSignInSucceded();
        void onSignInFailed(boolean wasTrySilently);
        void onSignInCanceled();
        void onSignOutCompleted();

        /**
         * @param errorDialog contains a detailed error information in the dialog for user.
         */
        void showErrorDialog(Dialog errorDialog);

        void handleException(Exception e, String msgForUser);
    }

    /**
     * When using leaderboards score/ranks results implement this notify gameServicesListener in your class
     * and
     */
    public interface LeaderboardServicesListener {

        void onLeaderboardScoreResultCurrPlayer(String leaderboardId, LeaderboardUserScore userScore);
    }

    public static GameServices GetInstance() {
        return instance;
    }

    /**
     * Performs initialization on GameService object. Call this from onCreate(..) method.
     *
     * @param activity must to implement {@link GameServicesListener}.
     * @param setClients enum flags to determine which Google Clients to include in initialization.
     *                   To pass a several enums use EnumSet.of(...).
     */
    public void init(Activity activity, EnumSet<SetClient> setClients) {
        if (!(activity instanceof GameServicesListener)) {
            throw new ClassCastException(activity.getLocalClassName()
                    + " must implement GameServicesListener!");
        }
        this.activity = activity;
        appContext = activity.getApplicationContext();
        gameServicesListener = (GameServicesListener) activity;
        try {
            googleSignInClient = GoogleSignIn.getClient(activity,
                    new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN).build());
        } catch (NullPointerException e) {
            Log.e(e);
        }
        setClientsFlags = setClients;
    }

    /**
     * TODO finish all future clients
     */
    private void initGoogleClients(GoogleSignInAccount googleSignInAccount) {
        Iterator<SetClient> iterator = setClientsFlags.iterator();
        while (iterator.hasNext()) {
            SetClient setClient = iterator.next();
            String strMethodName = "set" + setClient.toString() + "Client";
            try {
                Method method = getClass().getDeclaredMethod(strMethodName,
                        new Class[] { GoogleSignInAccount.class });
                method.invoke(instance, googleSignInAccount);
            } catch (NoSuchMethodException e) {
                Log.e(e);
            } catch (IllegalAccessException e) {
                Log.e(e);
            } catch (InvocationTargetException e) {
                Log.e(e);
            }
        }
    }

    private void setAchievementsClient(GoogleSignInAccount googleSignInAccount) {
        achievementsClient = Games.getAchievementsClient(activity, googleSignInAccount);
        handleAchievementsSinceLastConnection();
    }

    private void setEventsClient(GoogleSignInAccount googleSignInAccount) {
        eventsClient = Games.getEventsClient(activity, googleSignInAccount);
    }

    private void setLeaderboardClient(GoogleSignInAccount googleSignInAccount) {
        leaderboardsClient = Games.getLeaderboardsClient(activity, googleSignInAccount);
        handleLeaderboardScoresSinceLastConnection();
    }

    private void setPlayersClient(GoogleSignInAccount googleSignInAccount) {
        playersClient = Games.getPlayersClient(activity, googleSignInAccount);
    }

    private void setMultiplayerClient(GoogleSignInAccount googleSignInAccount) {
    }

    public void setLeaderboardServicesListener(LeaderboardServicesListener boardServicesListener) {
        this.leaderboardServicesListener = boardServicesListener;
    }

    public void setTaskOnSignInSuccess(Runnable taskOnSignInSuccess) {
        this.taskOnSignInSuccess = taskOnSignInSuccess;
    }

    /**
     * Should be called in onResume().
     */
    public boolean isGooglePlayServicesAvailable(boolean showError) {
        GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
        int resultCode = googleApi.isGooglePlayServicesAvailable(appContext);

        if (resultCode != ConnectionResult.SUCCESS && showError) {
            Dialog errorDialog = googleApi.getErrorDialog(activity, resultCode, RC_UNUSED);
            gameServicesListener.showErrorDialog(errorDialog);
            return false;
        } else {
            return resultCode == ConnectionResult.SUCCESS;
        }
    }

    /**
     * Sign in without displaying UI.
     */
    public void signInSilently() {
        if (googleSignInClient == null) return;
        googleSignInClient.silentSignIn().addOnCompleteListener(activity,
                new OnCompleteListener<GoogleSignInAccount>() {
            @Override
            public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
                if (task.isSuccessful()) {
                    Log.i(TAG, "onComplete success");
                    onConnected(task.getResult());
                    gameServicesListener.onSignInSucceded();
                    runTaskOnSuccessSignIn();
                } else {
                    Log.w(TAG, "onComplete failed exception = " + task.getException());
                    onDisconnected();
                    gameServicesListener.onSignInFailed(true);
                }
            }
        });
    }

    /**
     * Displays Google Interactive UI.
     */
    public void signInInteractively() {
        if (googleSignInClient == null) return;
        activity.startActivityForResult(googleSignInClient.getSignInIntent(), RC_SIGN_IN);
    }

    public boolean isSignedIn() {
        return GoogleSignIn.getLastSignedInAccount(appContext) != null;
    }

    public void signOut() {
        if (!isSignedIn() || (googleSignInClient == null)) return;

        googleSignInClient.signOut().addOnCompleteListener(activity, new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    gameServicesListener.onSignOutCompleted();
                    onDisconnected();
                }
            }
        });
    }

    /**
     * Called when user successfully signed in. Initializes a chosen Google Clients.
     */
    private void onConnected(GoogleSignInAccount googleSignInAccount) {
        initGoogleClients(googleSignInAccount);
    }

    private void onDisconnected() {
        achievementsClient = null;
        eventsClient = null;
        leaderboardsClient = null;
        playersClient = null;
    }

    public void setUnlockAchievementIds(String[] unlockAchievementIds) {
        this.unlockAchievementIds = unlockAchievementIds;
    }

    public void setIncrementAchievementIds(String[] incrementAchievementIds) {
        this.incrementAchievementIds = incrementAchievementIds;
    }

    public void showAchievements() {
        Log.i("achievementsClient = " + achievementsClient);
        if (achievementsClient == null) return;
        achievementsClient.getAchievementsIntent()
                .addOnSuccessListener(new OnSuccessListener<Intent>() {
                    @Override
                    public void onSuccess(Intent intent) {
                        Log.i("onSuccess before starting activityForResult");
                        activity.startActivityForResult(intent, RC_ACHIEVEMENT_UI);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        gameServicesListener.handleException(e, appContext.getString(R.string.error_achievements));
                    }
                });
    }

    public void incrementAchievement(String achievementId, int incNum) {
        if ((achievementsClient != null) && isSignedIn()) {
            achievementsClient.increment(achievementId, incNum);
        } else {
            setAchievementIncrementSinceLastConnection(achievementId, incNum);
        }
    }

    public void unlockAchievement(String achievementId) {
        if ((achievementsClient != null) && isSignedIn()) {
            achievementsClient.unlock(achievementId);
        } else {
            setAchievementUnlocked(achievementId, true);
        }
    }

    private void setAchievementUnlocked(String achievementID, boolean unlocked) {
        activity.getPreferences(Context.MODE_PRIVATE).edit().putBoolean("unlocked"
                + achievementID, unlocked).commit();
    }

    public boolean getAchievementUnlocked(String achievementID) {
        return activity.getPreferences(Context.MODE_PRIVATE).getBoolean("unlocked"
                + achievementID, false);
    }


    private void setAchievementIncrementSinceLastConnection(String achievementID, int num) {
        activity.getPreferences(Context.MODE_PRIVATE).edit().putInt("incremented"
                + achievementID, num).commit();
    }

    private int getAchievementIncrementSinceLastConnection(String achievementID) {
        return activity.getPreferences(Context.MODE_PRIVATE).getInt("incremented"
                + achievementID, 0);
    }

    /**
     * Submits achievements statuses to server.
     */
    private void handleAchievementsSinceLastConnection() {
        for (String id : unlockAchievementIds) {
            if (getAchievementUnlocked(id)) {
                Log.i("Unlocking achievement " + id);
                unlockAchievement(id);
                setAchievementUnlocked(id, true);
            }
        }
        for (String id : incrementAchievementIds) {
            if (getAchievementIncrementSinceLastConnection(id) > 0) {
                Log.i("Incrementing achievement " + id);
                setAchievementIncrementSinceLastConnection(id, 0);
            }
        }
    }

    public void setLeaderboardIds(String[] leaderboardIds) {
        this.leaderboardIds = leaderboardIds;
    }

    public void submitScoreToLeaderboard(String leaderboardId, long score) {
        if ((leaderboardsClient != null) && isSignedIn()) {
            leaderboardsClient.submitScore(leaderboardId, score);
        } else {
            if (score > getMaxScoreSinceLastConnection(leaderboardId)) {
                setMaxScoreSinceLastConnection(leaderboardId, score);
            }
        }
    }

    private long getMaxScoreSinceLastConnection(String leaderboardId) {
        return activity.getPreferences(Context.MODE_PRIVATE).getLong("score_" + leaderboardId, -1);
    }

    private void setMaxScoreSinceLastConnection(String leaderboardId, long maxScore) {
        activity.getPreferences(Context.MODE_PRIVATE).edit().putLong("score_" + leaderboardId,
                maxScore).commit();
    }

    /**
     * Submits stored scores to server. '-1' indicates already submitted status.
     */
    private void handleLeaderboardScoresSinceLastConnection() {
        for (String id : leaderboardIds) {
            long score = getMaxScoreSinceLastConnection(id);
            if (score != -1) {
                if (Log.DEBUG) {
                    Log.i("Updating score = " + score + " for Leaderboard: " + id);
                }
                submitScoreToLeaderboard(id, score);
                setMaxScoreSinceLastConnection(id, -1);
            }
        }
    }

    public void showLeaderboards() {
        if (leaderboardsClient == null) return;
        leaderboardsClient.getAllLeaderboardsIntent()
                .addOnSuccessListener(new OnSuccessListener<Intent>() {
                    @Override
                    public void onSuccess(Intent intent) {
                        activity.startActivityForResult(intent, RC_LEADERBOARD_UI);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        gameServicesListener.handleException(e, appContext.getString(R.string.error_leaderboards));
                    }
                });
    }

    public void showLeaderboard(String leaderboardId) {
        if (leaderboardsClient == null) return;
        leaderboardsClient.getLeaderboardIntent(leaderboardId)
                .addOnSuccessListener(new OnSuccessListener<Intent>() {
                    @Override
                    public void onSuccess(Intent intent) {
                        activity.startActivityForResult(intent, RC_LEADERBOARD_UI);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        gameServicesListener.handleException(e, appContext.getString(R.string.error_leaderboards));
                    }
                });
    }

    /**
     * Retrieves asynchronously scores result for leaderboardId. If a result retrieved successfully
     * constructs data object {@link LeaderboardUserScore} and transfers result with notify listener.
     * <b>To use this function implement {@link LeaderboardServicesListener}</b>
     *
     * @param timeSpanOptions 0 - time span daily when scores are reset every day,
     *                        1 - time span weekly when scores are reset once per week.
     *                        2 - time span all time when scores are never reset. Used by default.
     */
    public void loadCurrentPlayerLeaderboardScore(final String leaderboardId, int timeSpanOptions) {
        if (leaderboardsClient == null) return;
        int timeSpanVariant = (timeSpanOptions < 0 || timeSpanOptions > 2) ? 2 : timeSpanOptions;

        leaderboardsClient.loadCurrentPlayerLeaderboardScore(leaderboardId, timeSpanVariant,
                LeaderboardVariant.COLLECTION_PUBLIC).addOnSuccessListener(
                        new OnSuccessListener<AnnotatedData<LeaderboardScore>>() {
            @Override
            public void onSuccess(AnnotatedData<LeaderboardScore> leaderboardScoreAnnotatedData) {
                LeaderboardScore scoreResult =  leaderboardScoreAnnotatedData.get();
                LeaderboardUserScore userScore = new LeaderboardUserScore();
                userScore.setDisplayRank(scoreResult.getDisplayRank());
                userScore.setDisplayScore(scoreResult.getDisplayScore());
                userScore.setPlayerName(scoreResult.getScoreHolderDisplayName());
                userScore.setRank(scoreResult.getRank());
                userScore.setRawScore(scoreResult.getRawScore());
                Log.i("xo", "loaded user score = " + scoreResult.getRawScore());
                leaderboardServicesListener.onLeaderboardScoreResultCurrPlayer(leaderboardId, userScore);
            }
        });
    }

    public void runTaskOnSuccessSignIn() {
        if (taskOnSignInSuccess != null) {
            taskOnSignInSuccess.run();
        }
    }

    /**
     * Handle activity result. Call this method from your Activity's onActivityResult(..) callback.
     * If the activity result pertains to the sign-in process, processes it appropriately.
     */
    public void onActivityResult(int requestCode, int responseCode, Intent intent) {
        if (Log.DEBUG) {
            Log.i("requestCode = " + requestCode + ", responseCode = " + responseCode
                    + ", intent = " + intent);
        }
        // if user canceled i.e back navigation.
        if (responseCode == Activity.RESULT_CANCELED) {
            if (requestCode == RC_SIGN_IN) gameServicesListener.onSignInCanceled();
            return;
        }
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(intent);
            Log.i("task = " + task);
            if ((task != null) && task.isSuccessful()) {
                Log.i("isSuccessful = true");
                GoogleSignInAccount account = task.getResult();
                onConnected(account);
                gameServicesListener.onSignInSucceded();
                runTaskOnSuccessSignIn();
            } else {
                Log.i("task exception = " + task.getException());
                onDisconnected();
                gameServicesListener.handleException(task.getException(), activity.getString(R.string.error_restart));
            }
        }
    }

    /**
     * Call this from destroy();
     */
    public void destroy() {
        //if (isSignedIn()) signOut();
        googleSignInClient = null;
        gameServicesListener = null;
        activity = null;
        appContext = null;
    }

}
