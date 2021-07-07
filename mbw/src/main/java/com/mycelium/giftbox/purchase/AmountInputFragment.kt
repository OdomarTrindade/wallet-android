package com.mycelium.giftbox.purchase

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.mycelium.giftbox.client.GitboxAPI
import com.mycelium.giftbox.client.models.PriceResponse
import com.mycelium.giftbox.client.models.getCardValue
import com.mycelium.wallet.*
import com.mycelium.wallet.activity.modern.Toaster
import com.mycelium.wallet.activity.util.toString
import com.mycelium.wallet.activity.util.toStringWithUnit
import com.mycelium.wallet.databinding.FragmentGiftboxAmountBinding
import com.mycelium.wapi.wallet.coins.AssetInfo
import com.mycelium.wapi.wallet.coins.Value
import com.mycelium.wapi.wallet.coins.Value.Companion.isNullOrZero
import com.mycelium.wapi.wallet.coins.Value.Companion.valueOf
import com.mycelium.wapi.wallet.fiat.coins.FiatType
import kotlinx.android.synthetic.main.fragment_giftbox_amount.*
import kotlinx.android.synthetic.main.layout_fio_request_notification.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

class AmountInputFragment : Fragment(), NumberEntry.NumberEntryListener {
    private lateinit var binding: FragmentGiftboxAmountBinding
    private var _numberEntry: NumberEntry? = null
    private lateinit var _mbwManager: MbwManager
    val args by navArgs<AmountInputFragmentArgs>()

    val zeroFiatValue by lazy {
        Value.zeroValue(Utils.getTypeByName(args.product.currencyCode)!!)
    }

    private val account by lazy {
        MbwManager.getInstance(requireContext()).getWalletManager(false)
            .getAccount(args.accountId)
    }

    private var _amount: Value? = null
        set(value) {
            field = value
            lifecycleScope.launch(IO) {
                getPriceResponse(value!!).collect {
                    withContext(Dispatchers.Main) {
                        val exchangeRate = BigDecimal(it!!.exchangeRate)
                        //update crypto amount
                        val cryptoAmountFromFiat =
                            value.valueAsLong.toBigDecimal()
                                .setScale(account?.coinType?.unitExponent!!) / toUnits(
                                Utils.getTypeByName(
                                    args.product.currencyCode!!
                                )!!, exchangeRate
                            ).toBigDecimal()
                        val cryptoAmountValue =
                            valueOf(
                                account?.basedOnCoinType!!,
                                toUnits(account?.basedOnCoinType!!, cryptoAmountFromFiat)
                            )
                        tvCryptoAmount.text = cryptoAmountValue.toStringWithUnit()

                        //update spendable
                        val maxSpendable = getMaxSpendable()
                        val fiatSpendable = maxSpendable?.valueAsBigDecimal?.multiply(exchangeRate)
                        tvSpendableAmount.text = valueOf(
                            zeroFiatValue.type,
                            toUnits(zeroFiatValue.type, fiatSpendable!!)
                        ).toStringWithUnit()
                    }
                }
            }
        }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate<FragmentGiftboxAmountBinding>(
            inflater,
            R.layout.fragment_giftbox_amount,
            container,
            false
        ).apply { lifecycleOwner = this@AmountInputFragment }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _mbwManager = MbwManager.getInstance(activity?.applicationContext)

        with(binding) {
            btOk.setOnClickListener {
                LocalBroadcastManager.getInstance(requireContext())
                    .sendBroadcast(Intent(ACTION_AMOUNT_SELECTED).apply {
                        putExtra(AMOUNT_KEY, _amount)
                    })
                findNavController().navigateUp()
            }
            btMax.setOnClickListener {
                setEnteredAmount(args.product.maximumValue.toPlainString()!!)
                _numberEntry!!.setEntry(args.product.maximumValue, getMaxDecimal(_amount?.type!!))
                checkEntry()
            }
            tvCardValue.text = args.product?.getCardValue()
        }

        initNumberEntry(savedInstanceState)
    }

    private fun getMaxSpendable() = account?.accountBalance?.spendable

    private fun getMaxDecimal(assetInfo: AssetInfo): Int {
        return (assetInfo as? FiatType)?.unitExponent
            ?: assetInfo.unitExponent - _mbwManager.getDenomination(_amount?.type).scale
    }

    fun zeroValue(): Value {
        return Value.zeroValue(Utils.getTypeByName(args.product.currencyCode)!!)
    }

    private fun toUnits(assetInfo: String, amount: BigDecimal): BigInteger =
        toUnits(Utils.getTypeByName(args.product.currencyCode)!!, amount)

    private fun toUnits(assetInfo: AssetInfo, amount: BigDecimal): BigInteger =
        amount.movePointRight(assetInfo.unitExponent).setScale(0, RoundingMode.HALF_UP)
            .toBigIntegerExact()

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putSerializable(ENTERED_AMOUNT, _amount)
    }

    private fun initNumberEntry(savedInstanceState: Bundle?) {
        // Load saved state
        if (savedInstanceState != null) {
            _amount = savedInstanceState.getSerializable(ENTERED_AMOUNT) as Value
        } else {
            _amount = args.amount ?: zeroValue()
        }

        // Init the number pad
        val amountString: String
        if (!isNullOrZero(_amount)) {
            val denomination = _mbwManager.getDenomination(_amount?.type)
            amountString = _amount?.toString(denomination) ?: ""
        } else {
            amountString = ""
        }
        _numberEntry = NumberEntry(getMaxDecimal(_amount?.type!!), this, activity, amountString)

        updateAmountsDisplay(amountString)
    }


    override fun onEntryChanged(entry: String, wasSet: Boolean) {
        if (!wasSet) {
            // if it was change by the user pressing buttons (show it unformatted)
            setEnteredAmount(entry)
        }
        updateAmountsDisplay(entry)
        checkEntry()
    }

    private fun updateAmountsDisplay(amountText: String) {
        binding.tvAmount.text = amountText

        binding.btCurrency.text = _amount?.currencySymbol?.toUpperCase()
    }


    private fun setEnteredAmount(value: String) {
        if (value.isEmpty()) {
            _amount = zeroValue()
        } else {
            _amount = _amount?.type?.value(value)
        }

        val insufficientFounds = _amount!!.moreThan(getMaxSpendable()!!)
        val exceedCardPrice = _amount!!.moreThan(
            valueOf(
                _amount!!.type,
                toUnits(args.product.currencyCode!!, args.product.maximumValue)
            )
        )
        val minimumPrice = valueOf(
            _amount!!.type,
            toUnits(args.product.currencyCode!!, args.product.minimumValue)
        )
        val lessMinimumCardPrice = _amount!!.lessThan(
            minimumPrice
        )

        binding.tvAmount.setTextColor(
            ResourcesCompat.getColor(
                resources,
                if (insufficientFounds || exceedCardPrice || lessMinimumCardPrice) R.color.red_error else R.color.white,
                null
            )
        )
        if (insufficientFounds) {
            Toaster(requireContext()).toast("Insufficient funds", true)
        }
        if (exceedCardPrice) {
            Toaster(requireContext()).toast("Exceed card value", true)
        }
        if (lessMinimumCardPrice) {
            Toaster(requireContext()).toast(
                "Minimal card value: " + minimumPrice.toStringWithUnit(),
                true
            )
        }

    }

    private fun checkEntry() {
        val valid = !isNullOrZero(_amount)
                && _amount!!.moreOrEqualThan(
            valueOf(
                _amount!!.type,
                toUnits(args.product.currencyCode!!, args.product.minimumValue)
            )
        )
                && _amount!!.lessOrEqualThan(
            valueOf(
                _amount!!.type,
                toUnits(args.product.currencyCode!!, args.product.maximumValue)
            )
        )
        binding.btOk.isEnabled = valid
    }

    private fun getPriceResponse(value: Value): Flow<PriceResponse?> {
        return callbackFlow {
            GitboxAPI.giftRepository.getPrice(lifecycleScope,
                code = args.product.code!!,
                quantity = args.quantity,
                amount = value.valueAsBigDecimal.toInt(),
                currencyId = account?.basedOnCoinType?.symbol?.removePrefix("t") ?: "",
                success = { priceResponse ->
                    if (priceResponse!!.status == PriceResponse.Status.eRROR) {
                        return@getPrice
                    }
                    offer(priceResponse)
                },
                error = { _, error ->
//                    val fromJson = Gson().fromJson(error, ErrorMessage::class.java)
                    close()
                },
                finally = {
                    close()
                })
            awaitClose { }
        }
    }

    private fun getCryptoAmount(price: String): Value {
        val cryptoUnit = BigDecimal(price).movePointRight(account?.basedOnCoinType?.unitExponent!!)
            .toBigInteger()
        return valueOf(account?.basedOnCoinType!!, cryptoUnit)
    }

    companion object {
        const val ACTION_AMOUNT_SELECTED: String = "action_amount"
        const val AMOUNT_KEY = "amount"
        const val ENTERED_AMOUNT = "enteredamount"
    }

}