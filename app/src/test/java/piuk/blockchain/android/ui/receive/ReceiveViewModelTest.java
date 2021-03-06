package piuk.blockchain.android.ui.receive;

import android.annotation.SuppressLint;
import android.app.Application;
import android.graphics.Bitmap;

import info.blockchain.wallet.payload.PayloadManager;
import info.blockchain.wallet.payload.data.Account;
import info.blockchain.wallet.payload.data.HDWallet;
import info.blockchain.wallet.payload.data.LegacyAddress;
import info.blockchain.wallet.payload.data.Wallet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.inject.Named;

import io.reactivex.Observable;
import piuk.blockchain.android.BlockchainTestApplication;
import piuk.blockchain.android.BuildConfig;
import piuk.blockchain.android.data.datamanagers.PayloadDataManager;
import piuk.blockchain.android.data.datamanagers.QrCodeDataManager;
import piuk.blockchain.android.data.rxjava.RxBus;
import piuk.blockchain.android.injection.ApiModule;
import piuk.blockchain.android.injection.ApplicationModule;
import piuk.blockchain.android.injection.DataManagerModule;
import piuk.blockchain.android.injection.Injector;
import piuk.blockchain.android.injection.InjectorTestUtils;
import piuk.blockchain.android.ui.account.ItemAccount;
import piuk.blockchain.android.ui.customviews.ToastCustom;
import piuk.blockchain.android.util.AppUtil;
import piuk.blockchain.android.util.ExchangeRateFactory;
import piuk.blockchain.android.util.PrefsUtil;
import piuk.blockchain.android.util.SSLVerifyUtil;
import piuk.blockchain.android.util.StringUtils;
import retrofit2.Retrofit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static piuk.blockchain.android.ui.receive.ReceiveViewModel.KEY_WARN_WATCH_ONLY_SPEND;

@SuppressWarnings("PrivateMemberAccessBetweenOuterAndInnerClass")
@Config(sdk = 23, constants = BuildConfig.class, application = BlockchainTestApplication.class)
@RunWith(RobolectricTestRunner.class)
public class ReceiveViewModelTest {

    private ReceiveViewModel subject;
    @Mock private PayloadDataManager payloadDataManager;
    @Mock private AppUtil appUtil;
    @Mock private PrefsUtil prefsUtil;
    @Mock private StringUtils stringUtils;
    @Mock private QrCodeDataManager qrCodeDataManager;
    @Mock private ExchangeRateFactory exchangeRateFactory;
    @Mock private WalletAccountHelper walletAccountHelper;
    @Mock private SSLVerifyUtil sslVerifyUtil;
    @Mock private ReceiveViewModel.DataListener activity;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        InjectorTestUtils.initApplicationComponent(
                Injector.getInstance(),
                new MockApplicationModule(RuntimeEnvironment.application),
                new MockApiModule(),
                new MockDataManagerModule());

        subject = new ReceiveViewModel(activity, Locale.UK);
    }

    @Test
    public void onViewReady() throws Exception {
        // Arrange
        Wallet mockPayload = mock(Wallet.class);
        HDWallet mockHdWallet = mock(HDWallet.class);

        List<Account> accounts = new ArrayList<>();
        Account account0 = new Account();
        account0.setArchived(true);
        Account account1 = new Account();
        Account account2 = new Account();
        accounts.addAll(Arrays.asList(account0, account1, account2));

        List<LegacyAddress> legacyAddresses = new ArrayList<>();
        LegacyAddress legacy0 = new LegacyAddress();
        legacy0.setTag(LegacyAddress.ARCHIVED_ADDRESS);
        LegacyAddress legacy1 = new LegacyAddress();
        LegacyAddress legacy2 = new LegacyAddress();
        legacy2.setLabel(null);
        legacy2.setPrivateKey("");
        LegacyAddress legacy3 = new LegacyAddress();
        legacy3.setLabel("Label");
        legacy3.setPrivateKey("");
        legacyAddresses.addAll(Arrays.asList(legacy0, legacy1, legacy2, legacy3));

        when(payloadDataManager.getWallet()).thenReturn(mockPayload);
        when(mockPayload.isUpgraded()).thenReturn(true);
        when(mockPayload.getHdWallets()).thenReturn(Collections.singletonList(mockHdWallet));
        when(mockHdWallet.getAccounts()).thenReturn(accounts);
        when(mockPayload.getLegacyAddressList()).thenReturn(legacyAddresses);
        // Act
        subject.onViewReady();
        // Assert
        verify(activity).onAccountDataChanged();
        assertEquals(5, subject.accountMap.size());
        assertEquals(2, subject.spinnerIndexMap.size());
    }

    @Test
    public void onSendToContactClickedInvalidAmount() throws Exception {
        // Arrange
        String errorString = "ERROR_STRING";
        when(stringUtils.getString(anyInt())).thenReturn(errorString);
        // Act
        subject.onSendToContactClicked("0.00");
        // Assert
        //noinspection WrongConstant
        verify(activity).showToast(errorString, ToastCustom.TYPE_ERROR);
        verifyZeroInteractions(activity);
    }

    @Test
    public void onSendToContactClickedValidAmount() throws Exception {
        // Arrange

        // Act
        subject.onSendToContactClicked("1.00");
        // Assert
        //noinspection WrongConstant
        verify(activity).startContactSelectionActivity();
        verifyZeroInteractions(activity);
    }

    @Test
    public void getReceiveToList() throws Exception {
        // Arrange
        when(walletAccountHelper.getAccountItems(anyBoolean())).thenReturn(Collections.emptyList());
        when(walletAccountHelper.getAddressBookEntries()).thenReturn(Collections.emptyList());
        // Act
        List<ItemAccount> values = subject.getReceiveToList();
        // Assert
        assertNotNull(values);
        assertTrue(values.isEmpty());
        verify(walletAccountHelper).getAccountItems(true);
        verify(walletAccountHelper).getAddressBookEntries();
    }

    @Test
    public void getCurrencyHelper() throws Exception {
        // Arrange

        // Act
        ReceiveCurrencyHelper value = subject.getCurrencyHelper();
        // Assert
        assertNotNull(value);
    }

    @Test
    public void generateQrCodeSuccessful() throws Exception {
        // Arrange
        when(qrCodeDataManager.generateQrCode(anyString(), anyInt()))
                .thenReturn(Observable.just(mock(Bitmap.class)));
        // Act
        subject.generateQrCode("test uri");
        // Assert
        verify(activity).showQrLoading();
        verify(activity).showQrCode(any(Bitmap.class));
    }

    @Test
    public void generateQrCodeFailure() throws Exception {
        // Arrange
        when(qrCodeDataManager.generateQrCode(anyString(), anyInt()))
                .thenReturn(Observable.error(new Throwable()));
        // Act
        subject.generateQrCode("test uri");
        // Assert
        verify(activity).showQrLoading();
        verify(activity).showQrCode(null);
    }

    @SuppressLint("NewApi")
    @Test
    public void getDefaultSpinnerPosition() throws Exception {
        // Arrange
        Wallet mockPayload = mock(Wallet.class);
        HDWallet mockHdWallet = mock(HDWallet.class);

        List<Account> accounts = new ArrayList<>();
        Account account0 = new Account();
        Account account1 = new Account();
        Account account2 = new Account();
        accounts.addAll(Arrays.asList(account0, account1, account2));

        when(payloadDataManager.getWallet()).thenReturn(mockPayload);
        when(payloadDataManager.getDefaultAccount()).thenReturn(account2);
        when(mockPayload.getHdWallets()).thenReturn(Collections.singletonList(mockHdWallet));
        when(mockHdWallet.getAccounts()).thenReturn(accounts);
        // Act
        subject.onViewReady(); // Update account list first
        Integer value = subject.getDefaultAccountPosition();
        // Assert
        assertEquals(2, Math.toIntExact(value));
    }

    @Test
    public void getAccountItemForPosition() throws Exception {
        // Arrange
        Wallet mockPayload = mock(Wallet.class);
        HDWallet mockHdWallet = mock(HDWallet.class);

        List<Account> accounts = new ArrayList<>();
        Account account0 = new Account();
        Account account1 = new Account();
        Account account2 = new Account();
        accounts.addAll(Arrays.asList(account0, account1, account2));

        when(payloadDataManager.getWallet()).thenReturn(mockPayload);
        when(mockPayload.getHdWallets()).thenReturn(Collections.singletonList(mockHdWallet));
        when(mockHdWallet.getAccounts()).thenReturn(accounts);
        // Act
        subject.onViewReady(); // Update account list first
        Object value = subject.getAccountItemForPosition(2);
        // Assert
        assertEquals(account2, value);
    }

    @Test
    public void warnWatchOnlySpend() throws Exception {
        // Arrange
        when(prefsUtil.getValue(anyString(), anyBoolean())).thenReturn(true);
        // Act
        Boolean value = subject.warnWatchOnlySpend();
        // Assert
        assertTrue(value);
    }

    @Test
    public void setWarnWatchOnlySpend() throws Exception {
        // Arrange

        // Act
        subject.setWarnWatchOnlySpend(true);
        // Assert
        verify(prefsUtil).setValue(KEY_WARN_WATCH_ONLY_SPEND, true);
    }

    @Test
    public void updateFiatTextField() throws Exception {
        // Arrange
        /**
         * This isn't reasonably testable in it's current form. The method relies on the
         * {@link ReceiveCurrencyHelper}, which needs to be injected. This is simple, but
         * would be best done after a large package refactor so that it can be scoped correctly.
         * This won't happen for a little bit.
         */
        // Act
        // TODO: 25/08/2016 Test me
        // Assert

    }

    @Test
    public void updateBtcTextField() throws Exception {
        // Arrange
        // See above
        // Act
        // TODO: 25/08/2016 Test me
        // Assert

    }

    @Test
    public void getV3ReceiveAddress() throws Exception {
        // Arrange
        Account account = new Account();
        when(payloadDataManager.getNextReceiveAddress(account))
                .thenReturn(Observable.just("test_address"));
        // Act
        subject.getV3ReceiveAddress(account);
        // Assert
        verify(payloadDataManager).getNextReceiveAddress(account);
        verifyNoMoreInteractions(payloadDataManager);
        verify(activity).updateReceiveAddress("test_address");
        verifyNoMoreInteractions(activity);
    }

    @Test
    public void getV3ReceiveAddressException() throws Exception {
        Account account = new Account();
        when(payloadDataManager.getNextReceiveAddress(account))
                .thenReturn(Observable.error(new Throwable()));
        when(stringUtils.getString(anyInt())).thenReturn(anyString());
        // Act
        subject.getV3ReceiveAddress(account);
        // Assert
        verify(activity).showToast(anyString(), eq(ToastCustom.TYPE_ERROR));
    }

    @Test
    public void getIntentDataList() throws Exception {
        // Arrange
        // This isn't reasonably testable in it's current form
        // TODO: 25/08/2016 More refactoring of this method
        // Act

        // Assert

    }

    private class MockApplicationModule extends ApplicationModule {

        MockApplicationModule(Application application) {
            super(application);
        }

        @Override
        protected PrefsUtil providePrefsUtil() {
            return prefsUtil;
        }

        @Override
        protected AppUtil provideAppUtil() {
            return appUtil;
        }

        @Override
        protected StringUtils provideStringUtils() {
            return stringUtils;
        }

        @Override
        protected ExchangeRateFactory provideExchangeRateFactory() {
            return exchangeRateFactory;
        }
    }

    private class MockApiModule extends ApiModule {
        @Override
        protected SSLVerifyUtil provideSSlVerifyUtil(@Named("server") Retrofit retrofit,
                                                     RxBus rxBus) {
            return sslVerifyUtil;
        }
    }

    private class MockDataManagerModule extends DataManagerModule {
        @Override
        protected QrCodeDataManager provideQrDataManager() {
            return qrCodeDataManager;
        }

        @Override
        protected WalletAccountHelper provideWalletAccountHelper(PayloadManager payloadManager,
                                                                 PrefsUtil prefsUtil,
                                                                 StringUtils stringUtils,
                                                                 ExchangeRateFactory exchangeRateFactory) {
            return walletAccountHelper;
        }

        @Override
        protected PayloadDataManager providePayloadDataManager(PayloadManager payloadManager,
                                                               RxBus rxBus) {
            return payloadDataManager;
        }
    }
}