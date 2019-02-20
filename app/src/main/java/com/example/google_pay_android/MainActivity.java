package com.example.google_pay_android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wallet.AutoResolveHelper;
import com.google.android.gms.wallet.IsReadyToPayRequest;
import com.google.android.gms.wallet.PaymentData;
import com.google.android.gms.wallet.PaymentDataRequest;
import com.google.android.gms.wallet.PaymentsClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Optional;

public class MainActivity extends AppCompatActivity {

  private PaymentsClient mPaymentsClient;

  private View mGooglePayButton;

  private static final int LOAD_PAYMENT_DATA_REQUEST_CODE = 991;

  private TextView mGooglePayStatusText;

  private ItemInfo mCarItem = new ItemInfo("Simple Car", 500 * 1000000, R.drawable.car);
  private long mShippingCost = 90 * 1000000;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    initItemUI();

    mGooglePayButton = findViewById(R.id.googlepay_button);
    mGooglePayStatusText = findViewById(R.id.googlepay_status);

    mPaymentsClient = PaymentsUtil.createPaymentsClient(this);
    possiblyShowGooglePayButton();

    mGooglePayButton.setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View view) {
                requestPayment(view);
              }
            });
  }

  private void possiblyShowGooglePayButton() {
    final Optional<JSONObject> isReadyToPayJson = PaymentsUtil.getIsReadyToPayRequest();
    if (!isReadyToPayJson.isPresent()) {
      return;
    }
    IsReadyToPayRequest request =
            IsReadyToPayRequest.fromJson(isReadyToPayJson.get().toString());
    if (request == null) {
      return;
    }
    Task<Boolean> task = mPaymentsClient.isReadyToPay(request);
    task.addOnCompleteListener(
            new OnCompleteListener<Boolean>() {
              @Override
              public void onComplete(Task<Boolean> task) {
                try {
                  boolean result = task.getResult(ApiException.class);
                  setGooglePayAvailable(result);
                } catch (ApiException exception) {
                  // Process error
                  Log.w("isReadyToPay failed", exception);
                }
              }
            });
  }
  private void setGooglePayAvailable(boolean available) {
    if (available) {
      mGooglePayStatusText.setVisibility(View.GONE);
      mGooglePayButton.setVisibility(View.VISIBLE);
    } else {
      mGooglePayStatusText.setText(R.string.googlepay_status_unavailable);
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      // value passed in AutoResolveHelper
      case LOAD_PAYMENT_DATA_REQUEST_CODE:
        switch (resultCode) {
          case Activity.RESULT_OK:
            PaymentData paymentData = PaymentData.getFromIntent(data);
            handlePaymentSuccess(paymentData);
            break;
          case Activity.RESULT_CANCELED:
            // Nothing to here normally - the user simply cancelled without selecting a
            // payment method.
            break;
          case AutoResolveHelper.RESULT_ERROR:
            Status status = AutoResolveHelper.getStatusFromIntent(data);
            handleError(status.getStatusCode());
            break;
          default:
            // Do nothing.
        }

        // Re-enables the Google Pay payment button.
        mGooglePayButton.setClickable(true);
        break;
    }
  }

  private void handlePaymentSuccess(PaymentData paymentData) {
    String paymentInformation = paymentData.toJson();

    // Token will be null if PaymentDataRequest was not constructed using fromJson(String).
    if (paymentInformation == null) {
      return;
    }
    JSONObject paymentMethodData;

    try {
      paymentMethodData = new JSONObject(paymentInformation).getJSONObject("paymentMethodData");

      if (paymentMethodData
              .getJSONObject("tokenizationData")
              .getString("type")
              .equals("PAYMENT_GATEWAY")
              && paymentMethodData
              .getJSONObject("tokenizationData")
              .getString("token")
              .equals("examplePaymentMethodToken")) {
        AlertDialog alertDialog =
                new AlertDialog.Builder(this)
                        .setTitle("Warning")
                        .setMessage(
                                "Gateway name set to \"example\" - please modify "
                                        + "Constants.java and replace it with your own gateway.")
                        .setPositiveButton("OK", null)
                        .create();
        alertDialog.show();
      }

      String billingName =
              paymentMethodData.getJSONObject("info").getJSONObject("billingAddress").getString("name");
      Log.d("BillingName", billingName);
      Toast.makeText(this, getString(R.string.payments_show_name, billingName), Toast.LENGTH_LONG)
              .show();
      Log.d("GooglePaymentToken", paymentMethodData.getJSONObject("tokenizationData").getString("token"));
    } catch (JSONException e) {
      Log.e("handlePaymentSuccess", "Error: " + e.toString());
      return;
    }
  }

  private void handleError(int statusCode) {
    Log.w("loadPaymentData failed", String.format("Error code: %d", statusCode));
  }
  public void requestPayment(View view) {
    // Disables the button to prevent multiple clicks.
    mGooglePayButton.setClickable(false);

    String price = PaymentsUtil.microsToString(mCarItem.getPriceMicros() + mShippingCost);

    Optional<JSONObject> paymentDataRequestJson = PaymentsUtil.getPaymentDataRequest(price);
    if (!paymentDataRequestJson.isPresent()) {
      return;
    }
    PaymentDataRequest request =
            PaymentDataRequest.fromJson(paymentDataRequestJson.get().toString());

    if (request != null) {
      AutoResolveHelper.resolveTask(
              mPaymentsClient.loadPaymentData(request), this, LOAD_PAYMENT_DATA_REQUEST_CODE);
    }
  }

  private void initItemUI() {
    TextView itemName = findViewById(R.id.text_item_name);
    ImageView itemImage = findViewById(R.id.image_item_image);
    TextView itemPrice = findViewById(R.id.text_item_price);

    itemName.setText(mCarItem.getName());
    itemImage.setImageResource(mCarItem.getImageResourceId());
    itemPrice.setText(PaymentsUtil.microsToString(mCarItem.getPriceMicros()));
  }
}