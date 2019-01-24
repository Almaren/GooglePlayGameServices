# Google Play Game & Billing Services

This is an Android utility lightweight lib for an easy use of Google Play Game Services and BillingServices writing in Java.
Fits good to use in a game. The sample project of LibGDX Game will be added soon. 
*Currenlty supports the following services: Leaderboard, Achievements, Billing.*

### Prerequisites

1) minSdkVersion 16
2) com.google.android.gms:play-services-games:16.0.0+           
   com.google.android.gms:play-services-auth:16.0.1+
3) classpath 'com.android.tools.build:gradle:3.2.+'   
   classpath 'com.google.gms:google-services:4.2.0'

### Getting Started

1) Download and import it as android module in your project.
   * settings.gradle: include ':GooglePlayGameServices'   
   * android build.gradle in a dependencies section: implementation project(':GooglePlayGameServices')
   * project build.gradle:
      ```
        buildscript {
            repositories {
                mavenLocal()
                mavenCentral()
                google()
                jcenter()
            }
            dependencies {
                classpath 'com.android.tools.build:gradle:3.2.1'
                classpath 'com.google.gms:google-services:4.2.0'
            }
        } 
      ```
      
  2) If you use proguard add these lines to your proguard-rules.pro file:
      ```
        #for proper work of Play Game Services
        -keep class com.almatime.gameservices.** {
            public protected private *;
        }
      ```
      
  3) Add app license key in GooglePlayGameServices/srs/main/res/values/string.xml:
        <string name="license_key">PUT YOUR APP LICENSE KEY FROM DEVELOPER CONSOLE in all "strings" translations</string>

  4) In your AndroidManifest.xml add:
     ```
      <uses-permission android:name="android.permission.INTERNET"/>
      <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
      <uses-permission android:name="com.android.vending.BILLING"/> // ADD ONLY IF YOU WILL USE BILLING SERVICE
      
      In <application>:
      <meta-data android:name="com.google.android.gms.games.APP_ID"
            android:value="@string/app_id" />
      <meta-data android:name="com.google.android.gms.version"
            android:value="@integer/google_play_services_version"/>
     ```
     
  5) Add APP ID from *Developer Console -> Game Services -> Your App* to string.xml/app_id.
     If you still haven't added an app in Game Services and credentials, read the below instructions.
  
### Game Services Usage
A singleton **GameServices** class provides Google authentication, Google Play services API. 
Implement GameServicesListener, and the following functions of the class must be called for full cycle initialization:

 * 1. init(Activity, EnumSet) call it from your activity onCreate(..)
 * 2. onResume() or from from your activity onStart() method.
 * 3. onActivityResult(int, int, Intent)} call it from your activity onActivityResult(..)
 * 4. destroy() call it from your activity destroy()

 When using achievements assign a required ids before calling GameServices.GetInstance().init(Activity, EnumSet):
 GameServices.GetInstance().setUnlockAchievementIds(String[])
 GameServices.GetInstance().setIncrementAchievementIds(String[])
 
 When using leaderboards assign an ids before calling GameServices.GetInstance().init(Activity, EnumSet):
 GameServices.GetInstance().setLeaderboardIds(String[])

*The sample use in your MainActivity class:*

  ```java
  public class MainActivity extends AndroidApplication implements GameServices.GameServicesListener {
  
    public static final int RC_LEADERBOARD_MY_ACTIVITY = 108;

    @Override
    protected void onCreate (Bundle savedInstanceState) {
      ...
      // -------------- instantiation of leaderbaords & achievements {name, id} ---------------
      LinkedHashMap<String, String> leaderboards = (LinkedHashMap<String, String>)
          Xml.GetHashMapResource(getApplicationContext(), R.xml.leaderboards);
      LeaderboardScore.GetInstance().setLeaderboardIds(leaderboards);
      Set<String> keys = leaderboards.keySet();
      GameServices.GetInstance().setLeaderboardIds(keys.toArray(new String[keys.size()]));

      LinkedHashMap<String, String> achievements = (LinkedHashMap<String, String>)
          Xml.GetHashMapResource(getApplicationContext(), R.xml.achievements);
      keys = achievements.keySet();
      GameServices.GetInstance().setUnlockAchievementIds(keys.toArray(new String[keys.size()]));

      LinkedHashMap<String, String> incAchievements = (LinkedHashMap<String, String>)
          Xml.GetHashMapResource(getApplicationContext(), R.xml.inc_achievements);
      keys = incAchievements.keySet();
      GameServices.GetInstance().setIncrementAchievementIds(keys.toArray(new String[keys.size()]));

      GameServices.GetInstance().init(this, EnumSet.of(SetClient.ACHIEVEMENTS, SetClient.LEADERBOARD));  
    }

    @Override
    protected void onStart() {
      GameServices.GetInstance().signInSilently();
      super.onStart();
    }

    @Override
    protected void onDestroy() {
      GameServices.GetInstance().destroy();
      super.onDestroy();
      System.exit(0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
      super.onActivityResult(requestCode, resultCode, data);
      GameServices.GetInstance().onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onSignInFailed(boolean wasTrySilently) {
      if (wasTrySilently) {
        GameServices.GetInstance().signInInteractively();
      }
    }

    @Override
    public void onSignInCanceled() {
      Settings.GetInstance().setSignInDeclined(true);
    }

    @Override
    public void showErrorDialog(Dialog errorDialog) {
      errorDialog.show();
    }
  }
  ```

### Billing Services Usage
A singleton **BillingService** provides **no ads purchase option**. Your activity must implement BillingServicesListener.
 * 1. {@link #init(Activity)} call it onCreate() or at first use of BillingServices.
 * 2. {@link #destroy()} call in your onDestroy() method.
 * 3. {@link #onActivityResult(int, int, Intent)} call it from your onActivityResult(..)
 
To start purchase no ads flow call -> BillingService.GetInstance().purchaseNoAds()
 
 ```java
 public class MainActivity extends AndroidApplication implements BillingServices.BillingServicesListener {
    @Override
	protected void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        ...
        BillingServices.GetInstance().init(this);
    }
  
    @Override
	protected void onDestroy() {
        BillingServices.GetInstance().destroy();
        super.onDestroy();
		System.exit(0);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        BillingServices.GetInstance().onActivityResult(requestCode, resultCode, data);
    }

    @Override
	public void onQueryInventoryCompleted(boolean isPurchasedNoAds) {
		Settings.GetInstance().setShowAds(!isPurchasedNoAds);
        
		if (isPurchasedNoAds && (SceneManager.GetInstance().getCurrSceneType()
				== SceneManager.SceneType.MAIN_MENU)) {
			((MainMenuScene) SceneManager.GetInstance().getCurrScene()).hideNoAdsImg();
		}
	}

	@Override
	public void onPurchaseFlowCompleted(boolean isSuccess) {
		if (isSuccess) {
			Settings.GetInstance().setShowAds(false);
			Settings.GetInstance().saveNoAdsSetting();
			if (SceneManager.GetInstance().getCurrSceneType() == SceneManager.SceneType.MAIN_MENU) {
				((MainMenuScene) SceneManager.GetInstance().getCurrScene()).hideNoAdsImg();
			}
			showMsg(false, getString(R.string.remove_ads_thank_you_message));
		} else {
			showMsg(true, getString(R.string.error_restart));
		}
	}

	@Override
	public void onBillingError(Exception e, String msgForUser) {
		showMsg(true, msgForUser);
	}
 }       
 ```
