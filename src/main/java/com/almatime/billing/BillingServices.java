package com.almatime.billing;

import android.app.Activity;
import android.content.Intent;

import com.almatime.gameservices.R;
import com.almatime.utils.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides no ads purchase option. Activity must implement {@link BillingServicesListener}.
 * 1) {@link #init(Activity)} call it onCreate() or at first use of BillingServices.
 * 2) {@link #destroy()} call in your onDestroy() method.
 * 3) {@link #onActivityResult(int, int, Intent)} call it from your onActivityResult(..)
 *
 * <b>R.string.license_key</b> - put your developer public encoded app key.
 *
 * @author Alexander Khrapunsky
 * @version 1.0.0, 09/11/2018.
 * @since 1.0.0
 */
public class BillingServices {

    private static BillingServices instance = new BillingServices();

    public static final int RC_PURCHASE = 10001;

    private Activity activity;
    private BillingServicesListener listener;

    private IabHelper iabHelper;

    private boolean inAppBillingSetup = false;
    private final String SKU_REMOVE_ADS = "no_ads";

    public interface BillingServicesListener {

        void onQueryInventoryCompleted(boolean isPurchasedNoAds);
        void onPurchaseFlowCompleted(boolean isSuccess);
        void onBillingError(Exception e, String msgForUser);
    }

    public static BillingServices GetInstance() {
        return instance;
    }

    public boolean isInAppBillingSetup() {
        return inAppBillingSetup;
    }

    public void init(Activity activity) {
        this.activity = activity;
        if (!(activity instanceof BillingServicesListener)) {
            throw new ClassCastException(activity.getLocalClassName()
                    + " must implement BillingServicesListener!");
        }
        listener = (BillingServicesListener) activity;

        String base64EncodedPublicKey = activity.getString(R.string.license_key);

        // compute your public key and store it in base64EncodedPublicKey
        try {
            iabHelper = new IabHelper(activity, base64EncodedPublicKey);
            iabHelper.enableDebugLogging(false); // TODO disable logging
            iabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
                public void onIabSetupFinished(IabResult result) {
                    if (!result.isSuccess()) {
                        // Oh no, there was a problem.
                        Log.w("Problem setting up In-app Billing: " + result);
                        //showAlertMsg(getString(R.string.error_restart));
                        inAppBillingSetup = false;
                        return;
                    }
                    if (iabHelper == null) return; // if already disposed in meantime
                    queryInventory();
                    inAppBillingSetup = true;
                }
            });
        } catch (IllegalStateException e) {
            // already disposed or initialized
            Log.e(e);
        } catch (Exception e) {
            Log.e(e);
        }
    }

    public void queryInventory() {
        List<String> additionalSkuList = new ArrayList<String>();
        additionalSkuList.add(SKU_REMOVE_ADS);
        try {
            iabHelper.queryInventoryAsync(true, additionalSkuList, null, queryFinishedListener);
        } catch (IabHelper.IabAsyncInProgressException e) {
            Log.e(e);
            listener.onBillingError(e, activity.getString(R.string.error_restart));
        } catch (IllegalStateException e) {
            Log.e(e);
        } catch (Exception e) {
            Log.e(e);
        }
    }

    /**
     * Before calling it check if Network is available and Google Services!
     */
    public void purchaseNoAds() {
        if (!inAppBillingSetup || (iabHelper == null)) {
            if (activity != null) init(activity);
        }
        if (inAppBillingSetup) {
            try {
                iabHelper.launchPurchaseFlow(activity, SKU_REMOVE_ADS, RC_PURCHASE, purchaseFinishedListener, "");
            } catch (Exception e) {
                listener.onBillingError(e, activity.getString(R.string.unknown_error_check_restart));
            }
        }
    }

    public void destroy() {
        if (iabHelper != null) {
            try {
                iabHelper.dispose();
            } catch (Exception e) {
                Log.e(e);
            }
            iabHelper = null;
        }
    }

    /**
     * Handle activity result. Call this method from your Activity's onActivityResult(..) callback.
     * If the activity result pertains to the purchase flow process, processes it appropriately.
     */
    public void onActivityResult(int requestCode, int responseCode, Intent intent) {
        if (requestCode == RC_PURCHASE) {
            // Pass on the activity result to the helper for handling purchase flow.
            iabHelper.handleActivityResult(requestCode, responseCode, intent);
        }
    }

    /**
     * This is a callback for after querying IAPs from Google Play Dev Console.
     */
    IabHelper.QueryInventoryFinishedListener queryFinishedListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            Log.i("Query inventory finished.");
            if ((iabHelper == null) || (listener == null)) return; // already disposed in meantime

            if (result.isFailure()) {
                // handle error
                Log.w("QueryInventory Error: " + result.toString());
                return;
            }
            Log.i("onQueryInventoryFinished is purchased = " + inventory.hasPurchase(SKU_REMOVE_ADS));

            listener.onQueryInventoryCompleted(inventory.hasPurchase(SKU_REMOVE_ADS));
        }
    };

    /**
     * Callback for when a purchase is finished.
     */
    IabHelper.OnIabPurchaseFinishedListener purchaseFinishedListener =
            new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.i("Purchase flow finished. " + result.getMessage() + ", response int = " + result.getResponse());
            if ((iabHelper == null) || (listener == null)) {
                return; //if disposed of in the meantime
            }
            if ((result.getResponse() == IabHelper.BILLING_RESPONSE_RESULT_USER_CANCELED)
                    || (result.getResponse() == IabHelper.IABHELPER_USER_CANCELLED)) {
                return; // if user cancelled operation
            }
            if (result.isSuccess()) {
                listener.onPurchaseFlowCompleted(true);
            } else {
                switch (result.getResponse()) {
                    case IabHelper.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED:
                        listener.onBillingError(null, activity.getString(R.string.error_billing_already_owned));
                        break;

                    default:
                        listener.onBillingError(null, activity.getString(R.string.error_restart));
                }
            }
        }
    };

}
