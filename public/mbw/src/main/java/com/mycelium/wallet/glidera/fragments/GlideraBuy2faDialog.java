package com.mycelium.wallet.glidera.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.mrd.bitlib.model.Address;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.glidera.GlideraUtils;
import com.mycelium.wallet.glidera.activities.GlideraTransaction;
import com.mycelium.wallet.glidera.api.GlideraService;
import com.mycelium.wallet.glidera.api.request.BuyRequest;
import com.mycelium.wallet.glidera.api.response.BuyResponse;
import com.mycelium.wallet.glidera.api.response.GlideraError;
import com.mycelium.wallet.glidera.api.response.TwoFactorResponse;

import java.math.BigDecimal;
import java.util.UUID;

import rx.Observer;

public class GlideraBuy2faDialog extends DialogFragment {
    private MbwManager mbwManager;
    private GlideraService glideraService;
    private String buyPriceResponseQty;
    private String buyPriceResponseTotal;
    private String buyPriceResponseUUID;
    private String mode2FA;
    private EditText et2FA;

    static GlideraBuy2faDialog newInstance(BigDecimal qty, BigDecimal total, TwoFactorResponse.Mode mode, UUID buyPriceResponseUUID) {
        Bundle bundle = new Bundle();
        bundle.putString("buyPriceResponseQty", qty.toPlainString());
        bundle.putString("buyPriceResponseTotal", total.toPlainString());
        bundle.putString("buyPriceResponseUUID", buyPriceResponseUUID.toString());
        bundle.putString("mode2FA", mode.toString());

        GlideraBuy2faDialog glideraBuy2faDialog = new GlideraBuy2faDialog();
        glideraBuy2faDialog.setArguments(bundle);

        return glideraBuy2faDialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = Preconditions.checkNotNull(inflater.inflate(R.layout.glidera_dialog_2fa, container, false));

        TextView tvPurchaseSummary = (TextView) root.findViewById(R.id.tvPurchaseSummary);
        TextView tv2FASummary = (TextView) root.findViewById(R.id.tv2FASummary);
        Button buttonResend2FA = (Button) root.findViewById(R.id.buttonResend2FA);
        et2FA = (EditText) root.findViewById(R.id.et2FA);

        getDialog().setTitle("Confirm Your Purchase");

        String purchaseSummary = "You are about to buy " + GlideraUtils.formatBtcForDisplay(new BigDecimal(buyPriceResponseQty)) + " for " +
                GlideraUtils.formatFiatForDisplay(new BigDecimal(buyPriceResponseTotal)) + ".";

        tvPurchaseSummary.setText(purchaseSummary);

        if (mode2FA.equals(TwoFactorResponse.Mode.NONE.toString())) {
            tv2FASummary.setVisibility(View.GONE);
            buttonResend2FA.setVisibility(View.GONE);
            et2FA.setVisibility(View.GONE);
        } else if (mode2FA.equals(TwoFactorResponse.Mode.AUTHENTICATR.toString())) {
            String twoFASummary = "Please enter your 2-factor authorization (2FA) code from your Authenticator smartphone app to complete" +
                    " this purchase.";
            tv2FASummary.setText(twoFASummary);
            buttonResend2FA.setVisibility(View.GONE);
            et2FA.setHint("2FA Code");
        } else if (mode2FA.equals(TwoFactorResponse.Mode.PIN.toString())) {
            String twoFASummary = "Please enter your PIN to complete this purchase.";
            tv2FASummary.setText(twoFASummary);
            buttonResend2FA.setVisibility(View.GONE);
            et2FA.setHint("PIN");
        } else if (mode2FA.equals(TwoFactorResponse.Mode.SMS.toString())) {
            String twoFASummary = "A text message has been sent to your phone with a 2-factor authentication (2FA) code. Please enter it " +
                    "to confirm this purchase.";
            tv2FASummary.setText(twoFASummary);
            et2FA.setHint("2FA Code");
        }

        Button buttonCancel = (Button) root.findViewById(R.id.buttonCancel);
        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                GlideraBuy2faDialog.this.getDialog().cancel();
            }
        });

        final Button buttonContinue = (Button) root.findViewById(R.id.buttonContinue);
        buttonContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                buttonContinue.setEnabled(false);
                final String twoFACode;

                /*
                Validate the 2fa or pin entered if two factor mode is anything other than none
                 */
                if (!mode2FA.equals(TwoFactorResponse.Mode.NONE.toString())) {
                    twoFACode = et2FA.getText().toString();

                    if (twoFACode == null || twoFACode.isEmpty()) {
                        if (mode2FA.equals(TwoFactorResponse.Mode.PIN.toString()))
                            et2FA.setError("PIN is required");
                        else
                            et2FA.setError("2FA Code is required");
                        buttonContinue.setEnabled(true);
                        return;
                    }
                } else {
                    twoFACode = null;
                }

                Optional<Address> receivingAddress = mbwManager.getSelectedAccount().getReceivingAddress();
                if (receivingAddress.isPresent()) {
                    Address address = receivingAddress.get();
                    BigDecimal qty = new BigDecimal(buyPriceResponseQty);
                    UUID uuid = UUID.fromString(buyPriceResponseUUID);

                    BuyRequest buyRequest = new BuyRequest(address, qty, uuid, false, null);

                    glideraService.buy(buyRequest, twoFACode).subscribe(new Observer<BuyResponse>() {
                        @Override
                        public void onCompleted() {

                        }

                        @Override
                        public void onError(Throwable e) {
                            GlideraError error = GlideraService.convertRetrofitException(e);
                            if (error != null && error.getCode() != null) {
                                if (error.getCode() == 2006) {
                                    if (mode2FA.equals(TwoFactorResponse.Mode.PIN.toString()))
                                        et2FA.setError("Incorrect PIN");
                                    else
                                        et2FA.setError("Incorrect 2FA Code");
                                }
                            }
                            buttonContinue.setEnabled(true);
                        }

                        @Override
                        public void onNext(BuyResponse buyResponse) {
                            Intent intent = new Intent(getActivity(), GlideraTransaction.class);
                            Bundle bundle = new Bundle();
                            bundle.putString("transactionuuid", buyResponse.getTransactionUuid().toString());
                            intent.putExtras(bundle);
                            startActivity(intent);
                        }
                    });
                }
            }
        });

        buttonResend2FA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                glideraService.getTwoFactor()
                        .subscribe(new Observer<TwoFactorResponse>() {
                            @Override
                            public void onCompleted() {
                            }

                            @Override
                            public void onError(Throwable e) {
                            }

                            @Override
                            public void onNext(TwoFactorResponse twoFactorResponse) {
                                //New 2fa code was sent
                            }
                        });
            }
        });

        return root;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        glideraService = GlideraService.getInstance();
        mbwManager = MbwManager.getInstance(this.getActivity());

        buyPriceResponseQty = getArguments().getString("buyPriceResponseQty");
        buyPriceResponseTotal = getArguments().getString("buyPriceResponseTotal");
        buyPriceResponseUUID = getArguments().getString("buyPriceResponseUUID");
        mode2FA = getArguments().getString("mode2FA");
    }
}
