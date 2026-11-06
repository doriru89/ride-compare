package com.orz.ridecompare;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newFixedThreadPool(2);
  private Runnable suggest;
  private int importGeneration;
  private boolean importing, backgroundSearch;
  private int pendingSearchAction, launchGeneration;
  private LinearLayout root, results, chosenRow, addressCard;
  private ScrollView contentScroll;
  private final ArrayList<View> browseOnly = new ArrayList<>();
  private boolean keyboardShowing, editingPickupMode;
  private ProgressBar addressSpinner;
  private TextView status, chosen, pickup, locateButton, clearButton;
  private EditText query, pickupQuery;
  private final PickupState pickupState = new PickupState();
  private LinearLayout pickupCard, pickupResults;
  private TextView pickupLabelView, pickupCheck, pickupError;
  private ProgressBar pickupSpinner;
  private Runnable pickupSuggest;
  private int pickupGeneration;
  private boolean settingPickupText, pickupSearching;
  private String pickupAddress = "当前位置";
  private CompareButton compare;
  private SavedPlacesDialog favoritesDialog;
  private Address destination;
  private Location origin;
  private boolean locating, searching, splitPending;
  private int pendingAction, queryGeneration;
  private CancellationSignal locationCancel;
  private long splitStarted;
  private static final String UBER = "com.ubercab", LYFT = "me.lyft.android";
  private static final int INK = 0xFFF4F1FA,
      MUTED = 0xFFABA6B8,
      PURPLE = 0xFFB18AFF,
      BG = 0xFF202029;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    getWindow().getDecorView().setSystemUiVisibility(0);
    getWindow()
        .setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    getWindow().setDecorFitsSystemWindows(false);
    FrameLayout stage = new FrameLayout(this);
    stage.setBackgroundColor(BG);
    LinearLayout panel =
        new LinearLayout(this) {
          @Override
          protected void onMeasure(int w, int h) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(
                    Math.min(MeasureSpec.getSize(w), dp(640)), MeasureSpec.EXACTLY),
                h);
          }
        };
    panel.setOrientation(LinearLayout.VERTICAL);
    stage.addView(
        panel, new FrameLayout.LayoutParams(-1, -1, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
    setContentView(stage);
    stage.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
          Insets ime = insets.getInsets(WindowInsets.Type.ime());
          v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
          applyKeyboardMode(insets.isVisible(WindowInsets.Type.ime()));
          return insets;
        });
    ScrollView scroll = new ScrollView(this);
    contentScroll = scroll;
    scroll.setClipToPadding(false);
    scroll.setFillViewport(false);
    scroll.setVerticalScrollBarEnabled(false);
    panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    root = column();
    root.setPadding(dp(24), dp(18), dp(24), dp(8));
    scroll.addView(root);
    LinearLayout brand = row();
    brand.setGravity(Gravity.CENTER_VERTICAL);
    root.addView(brand);
    RouteGlyph logo = new RouteGlyph(this, 2);
    brand.addView(logo, new LinearLayout.LayoutParams(dp(34), dp(34)));
    TextView word = text("RIDE COMPARE", 12, INK);
    word.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    word.setLetterSpacing(.12f);
    LinearLayout.LayoutParams wordLp = new LinearLayout.LayoutParams(0, -2, 1);
    wordLp.leftMargin = dp(10);
    brand.addView(word, wordLp);
    TextView menu = chip("•••", INK, 0xFF292733);
    menu.setContentDescription("更多选项与帮助");
    brand.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(42)));
    menu.setOnClickListener(v -> showMenu(menu));
    TextView title = text("去哪儿？", 32, INK);
    title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    add(root, title, 0, 14, 0, 16);
    LinearLayout card = column();
    addressCard = card;
    card.setPadding(dp(24), dp(24), dp(24), dp(22));
    card.setBackground(new NeoDrawable(getResources().getDisplayMetrics().density, true));
    card.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    root.addView(card);
    LinearLayout fieldHeader = row();
    fieldHeader.setGravity(Gravity.CENTER_VERTICAL);
    TextView paste = text("Paste from clipboard", 12, PURPLE);
    paste.setSingleLine(true);
    paste.setEllipsize(TextUtils.TruncateAt.END);
    paste.setGravity(Gravity.CENTER_VERTICAL);
    paste.setMinHeight(dp(40));
    paste.setClickable(true);
    paste.setFocusable(true);
    paste.setContentDescription("Paste from clipboard，粘贴剪贴板地址");
    paste.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x22713DE8),
            shape(Color.TRANSPARENT, 10, 0),
            shape(Color.WHITE, 10, 0)));
    fieldHeader.addView(paste, new LinearLayout.LayoutParams(0, dp(40), 1));
    paste.setOnClickListener(v -> pasteAddress());
    ImageView maps = new ImageView(this);
    try {
      maps.setImageDrawable(getPackageManager().getApplicationIcon("com.google.android.apps.maps"));
    } catch (Exception e) {
      maps.setImageResource(android.R.drawable.ic_menu_mapmode);
    }
    maps.setScaleType(ImageView.ScaleType.FIT_CENTER);
    maps.setPadding(dp(8), dp(8), dp(8), dp(8));
    maps.setContentDescription("打开 Google Maps 搜索地点");
    maps.setTooltipText("Google Maps");
    maps.setClickable(true);
    maps.setFocusable(true);
    maps.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x22713DE8),
            shape(Color.TRANSPARENT, 10, 0),
            shape(Color.WHITE, 10, 0)));
    LinearLayout.LayoutParams mapsLp = new LinearLayout.LayoutParams(dp(40), dp(40));
    mapsLp.leftMargin = dp(8);
    mapsLp.rightMargin = dp(8);
    maps.setOnClickListener(v -> openMaps());
    TextView saved = text("常用地址", 14, PURPLE);
    saved.setPadding(dp(8), dp(6), 0, dp(6));
    saved.setMinHeight(dp(40));
    saved.setGravity(Gravity.CENTER_VERTICAL);
    saved.setClickable(true);
    saved.setFocusable(true);
    saved.setContentDescription("常用地址，选择或添加已保存地点");
    fieldHeader.addView(saved);
    saved.setOnClickListener(v -> showFavorites(saved));
    fieldHeader.addView(maps, mapsLp);
    add(card, fieldHeader, 0, 0, 0, 4);
    LinearLayout input = row();
    input.setGravity(Gravity.CENTER_VERTICAL);
    input.setMinimumHeight(dp(48));
    card.addView(input);
    query = new EditText(this);
    query.setTextSize(21);
    query.setTextColor(INK);
    query.setHintTextColor(0xFF9D9DA6);
    query.setHint("输入目的地");
    query.setBackgroundColor(Color.TRANSPARENT);
    query.setPadding(0, dp(3), dp(8), dp(3));
    query.setFilters(new InputFilter[] {new InputFilter.LengthFilter(1200)});
    query.setMaxLines(3);
    query.setMinLines(1);
    query.setInputType(
        InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    query.setContentDescription("目的地，输入地点名称或完整地址");
    input.addView(query, new LinearLayout.LayoutParams(0, -2, 1));
    addressSpinner = new ProgressBar(this);
    addressSpinner.setIndeterminate(true);
    addressSpinner.setIndeterminateTintList(ColorStateList.valueOf(PURPLE));
    addressSpinner.setVisibility(View.GONE);
    LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(dp(18), dp(18));
    spinnerLp.rightMargin = dp(8);
    input.addView(addressSpinner, spinnerLp);
    clearButton = text("×", 22, MUTED);
    clearButton.setContentDescription("清空目的地");
    clearButton.setGravity(Gravity.CENTER);
    clearButton.setVisibility(View.GONE);
    clearButton.setClickable(true);
    clearButton.setFocusable(true);
    input.addView(clearButton, new LinearLayout.LayoutParams(dp(40), dp(44)));
    clearButton.setOnClickListener(
        v -> {
          query.setText("");
          query.requestFocus();
          ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
              .showSoftInput(query, InputMethodManager.SHOW_IMPLICIT);
        });
    chosenRow = row();
    chosenRow.setGravity(Gravity.CENTER_VERTICAL);
    chosenRow.setPadding(dp(12), dp(12), dp(12), dp(12));
    chosenRow.setBackground(shape(0xFF302541, 14, 0xFF79509B));
    chosenRow.setVisibility(View.GONE);
    chosen = text("", 14, INK);
    chosen.setMaxLines(3);
    chosen.setEllipsize(TextUtils.TruncateAt.END);
    chosenRow.addView(chosen, new LinearLayout.LayoutParams(0, -2, 1));
    TextView check = chip("✓", 0xFFF6F0FF, 0xFF713DE8);
    check.setClickable(false);
    check.setFocusable(false);
    check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    LinearLayout.LayoutParams tickLp = new LinearLayout.LayoutParams(dp(32), dp(32));
    tickLp.leftMargin = dp(12);
    chosenRow.addView(check, tickLp);
    add(card, chosenRow, 0, 6, 0, 0);
    results = column();
    results.setVisibility(View.GONE);
    add(card, results, 0, 6, 0, 0);
    status = text("", 13, 0xFFFFADB9);
    status.setVisibility(View.GONE);
    add(card, status, 0, 8, 0, 0);
    TextView pickupLabel = text("上车点", 12, MUTED);
    pickupLabelView = pickupLabel;
    add(root, pickupLabel, 0, 14, 0, 6);
    LinearLayout location = column();
    pickupCard = location;
    location.setPadding(dp(24), dp(18), dp(24), dp(18));
    location.setBackground(new NeoDrawable(getResources().getDisplayMetrics().density, true));
    location.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    root.addView(location);
    LinearLayout pickupRow = row();
    pickupRow.setGravity(Gravity.CENTER_VERTICAL);
    location.addView(pickupRow);
    RouteGlyph pin = new RouteGlyph(this, 1);
    pickupRow.addView(pin, new LinearLayout.LayoutParams(dp(36), dp(36)));
    LinearLayout place = column();
    LinearLayout.LayoutParams placeLp = new LinearLayout.LayoutParams(0, -2, 1);
    placeLp.leftMargin = dp(10);
    placeLp.rightMargin = dp(6);
    pickupRow.addView(place, placeLp);
    pickupQuery = new EditText(this);
    pickupQuery.setTextSize(17);
    pickupQuery.setTextColor(INK);
    pickupQuery.setHintTextColor(INK);
    pickupQuery.setHint("当前位置");
    pickupQuery.setBackgroundColor(Color.TRANSPARENT);
    pickupQuery.setPadding(0, dp(4), 0, dp(4));
    pickupQuery.setMinHeight(dp(40));
    pickupQuery.setMaxLines(2);
    pickupQuery.setFilters(new InputFilter[] {new InputFilter.LengthFilter(1200)});
    pickupQuery.setInputType(
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
    pickupQuery.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    pickupQuery.setContentDescription("上车地址，默认当前位置，可输入其他地址");
    place.addView(pickupQuery, new LinearLayout.LayoutParams(-1, -2));
    pickup = text("—", 12, MUTED);
    add(place, pickup, 0, 2, 0, 0);
    pickupCheck = text("✓", 20, PURPLE);
    pickupCheck.setVisibility(View.GONE);
    pickupCheck.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    pickupRow.addView(pickupCheck, new LinearLayout.LayoutParams(dp(26), -2));
    pickupSpinner = new ProgressBar(this);
    pickupSpinner.setIndeterminateTintList(ColorStateList.valueOf(PURPLE));
    pickupSpinner.setVisibility(View.GONE);
    pickupRow.addView(pickupSpinner, new LinearLayout.LayoutParams(dp(18), dp(18)));
    locateButton = chip("定位", PURPLE, Color.TRANSPARENT);
    pickupRow.addView(locateButton, new LinearLayout.LayoutParams(dp(80), dp(44)));
    locateButton.setOnClickListener(
        v -> {
          hideKeyboard();
          locate(0);
        });
    pickupResults = column();
    pickupResults.setVisibility(View.GONE);
    add(location, pickupResults, 0, 8, 0, 0);
    pickupError = text("", 13, 0xFFFFADB9);
    pickupError.setVisibility(View.GONE);
    add(location, pickupError, 0, 8, 0, 0);
    LinearLayout footer = column();
    footer.setPadding(dp(24), dp(12), dp(24), dp(20));
    panel.addView(footer);
    compare = new CompareButton(this);
    footer.addView(compare, new LinearLayout.LayoutParams(-1, dp(120)));
    compare.setOnClickListener(v -> prepare(3));
    browseOnly.addAll(Arrays.asList(brand, title, pickupLabel, location, footer));
    stage.setFocusableInTouchMode(true);
    stage.requestFocus();
    query.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            clearButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            queryGeneration++;
            launchGeneration++;
            splitPending = false;
            pendingSearchAction = 0;
            importGeneration++;
            importing = false;
            destination = null;
            chosenRow.setVisibility(View.GONE);
            results.removeAllViews();
            results.setVisibility(View.GONE);
            searching = false;
            pendingAction = 0;
            updateBusy();
            status.setVisibility(View.GONE);
            if (suggest != null) handler.removeCallbacks(suggest);
            if (s.length() >= 4 && !s.toString().matches("(?s).*https?://.*")) {
              suggest = () -> search(0, true);
              handler.postDelayed(suggest, 650);
            }
          }

          public void afterTextChanged(Editable s) {}
        });
    query.setOnEditorActionListener(
        (v, id, event) -> {
          if (id == EditorInfo.IME_ACTION_SEARCH) {
            search(0);
            return true;
          }
          return false;
        });
    query.setOnFocusChangeListener(
        (v, focused) -> {
          if (focused && keyboardShowing) applyKeyboardMode(true);
        });
    pickupQuery.setOnFocusChangeListener(
        (v, focused) -> {
          if (focused && keyboardShowing) applyKeyboardMode(true);
        });
    pickupQuery.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

          public void onTextChanged(CharSequence text, int start, int before, int count) {
            if (settingPickupText) return;
            pickupState.editManual();
            pickupGeneration++;
            launchGeneration++;
            splitPending = false;
            pendingAction = 0;
            origin = null;
            pickupSearching = false;
            if (locationCancel != null) {
              locationCancel.cancel();
              locationCancel = null;
            }
            locating = false;
            locateButton.setEnabled(true);
            locateButton.setText("当前位置");
            pickupQuery.setHint("输入上车地址");
            pickupCheck.setVisibility(View.GONE);
            pickup.setVisibility(View.GONE);
            pickupResults.removeAllViews();
            pickupResults.setVisibility(View.GONE);
            pickupError.setVisibility(View.GONE);
            status.setVisibility(View.GONE);
            if (pickupSuggest != null) handler.removeCallbacks(pickupSuggest);
            if (text.length() >= 4) {
              pickupSuggest = () -> searchPickup();
              handler.postDelayed(pickupSuggest, 650);
            }
            updateBusy();
          }

          public void afterTextChanged(Editable text) {}
        });
    pickupQuery.setOnEditorActionListener(
        (v, id, event) -> {
          if (id == EditorInfo.IME_ACTION_SEARCH) {
            searchPickup();
            return true;
          }
          return false;
        });
    if (state != null) {
      query.setText(state.getString("query", ""));
      if (state.containsKey("lat")) {
        destination = new Address(Locale.getDefault());
        destination.setLatitude(state.getDouble("lat"));
        destination.setLongitude(state.getDouble("lon"));
        destination.setAddressLine(0, state.getString("address", ""));
        showDestination();
        if (suggest != null) handler.removeCallbacks(suggest);
      }
    }
    if (state != null && state.getBoolean("manualPickup", false)) {
      String text = state.getString("pickupQuery", "");
      settingPickupText = true;
      pickupQuery.setText(text);
      settingPickupText = false;
      pickupState.editManual();
      locateButton.setText("当前位置");
      pickup.setVisibility(View.GONE);
      pickupQuery.setHint("输入上车地址");
      if (state.containsKey("pickupLat"))
        setManualPickup(
            state.getDouble("pickupLat"),
            state.getDouble("pickupLon"),
            state.getString("pickupAddress", text));
    }
    if (!pickupState.manual()
        && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) locate(0);
    if (state == null) receiveShare(getIntent());
    updateBusy();
  }

  private void applyKeyboardMode(boolean visible) {
    if (root == null) return;
    boolean changed = keyboardShowing != visible;
    keyboardShowing = visible;
    boolean editingPickup = visible && pickupQuery != null && pickupQuery.hasFocus();
    boolean targetChanged = editingPickupMode != editingPickup;
    editingPickupMode = editingPickup;
    for (View view : browseOnly) {
      boolean show = !visible || ((view == pickupCard || view == pickupLabelView) && editingPickup);
      view.setVisibility(show ? View.VISIBLE : View.GONE);
    }
    if (addressCard != null) addressCard.setVisibility(editingPickup ? View.GONE : View.VISIBLE);
    if (pickupCard != null)
      pickupCard.setVisibility(!visible || editingPickup ? View.VISIBLE : View.GONE);
    if (pickupLabelView != null)
      pickupLabelView.setVisibility(!visible || editingPickup ? View.VISIBLE : View.GONE);
    root.setPadding(dp(24), dp(visible ? 10 : 18), dp(24), dp(8));
    if (addressCard != null)
      addressCard.setPadding(dp(24), dp(visible ? 18 : 24), dp(24), dp(visible ? 14 : 22));
    if ((changed || targetChanged) && contentScroll != null)
      contentScroll.post(
          () -> {
            if (alive()) contentScroll.scrollTo(0, 0);
          });
  }

  private int dp(int x) {
    return (int) (getResources().getDisplayMetrics().density * x + .5f);
  }

  private LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.HORIZONTAL);
    return l;
  }

  private TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setIncludeFontPadding(false);
    return t;
  }

  private void add(LinearLayout parent, View view, int left, int top, int right, int bottom) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(dp(left), dp(top), dp(right), dp(bottom));
    parent.addView(view, lp);
  }

  private GradientDrawable shape(int color, int radius, int stroke) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    if (stroke != 0) d.setStroke(dp(1), stroke);
    return d;
  }

  private TextView chip(String s, int color, int bg) {
    TextView t = text(s, 14, color);
    t.setGravity(Gravity.CENTER);
    t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    t.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x226F3DE8), shape(bg, 14, 0), shape(Color.WHITE, 14, 0)));
    t.setClickable(true);
    t.setFocusable(true);
    return t;
  }

  private void updateBusy() {
    compare.setReady(destination != null && (!pickupState.manual() || origin != null));
    compare.setBusy(destination != null && locating);
    addressSpinner.setVisibility(searching || importing ? View.VISIBLE : View.GONE);
    pickupSpinner.setVisibility(pickupSearching ? View.VISIBLE : View.GONE);
  }

  private void say(String s) {
    TextView target =
        keyboardShowing && pickupQuery != null && pickupQuery.hasFocus() ? pickupError : status;
    target.setText(s);
    target.setVisibility(View.VISIBLE);
  }

  private boolean alive() {
    return !isFinishing() && !isDestroyed();
  }

  private String addressText(Address a) {
    String s = a.getMaxAddressLineIndex() >= 0 ? a.getAddressLine(0) : null;
    return s == null ? a.getLatitude() + ", " + a.getLongitude() : s;
  }

  private void showDestination() {
    chosen.setText(addressText(destination));
    chosenRow.setVisibility(View.VISIBLE);
    chosenRow.setContentDescription("已选中目的地，" + addressText(destination));
    results.removeAllViews();
    results.setVisibility(View.GONE);
    status.setVisibility(View.GONE);
    updateBusy();
  }

  private void showCandidates(List<Address> addresses, int action) {
    results.removeAllViews();
    results.setVisibility(View.VISIBLE);
    chosenRow.setVisibility(View.GONE);
    for (Address a : addresses) {
      LinearLayout option = row();
      option.setGravity(Gravity.CENTER_VERTICAL);
      option.setPadding(dp(12), dp(12), dp(12), dp(12));
      option.setBackground(
          new RippleDrawable(
              ColorStateList.valueOf(0x33713DE8),
              shape(0xFF292633, 14, 0xFF494054),
              shape(Color.WHITE, 14, 0)));
      option.setClickable(true);
      option.setFocusable(true);
      option.setContentDescription(addressText(a) + "，确认此目的地");
      TextView label = text(addressText(a), 14, INK);
      label.setMaxLines(3);
      label.setEllipsize(TextUtils.TruncateAt.END);
      option.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
      TextView confirm = text("✓", 20, PURPLE);
      confirm.setGravity(Gravity.CENTER);
      confirm.setBackground(shape(0xFF352B4C, 12, 0xFF79509B));
      confirm.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
      lp.leftMargin = dp(10);
      option.addView(confirm, lp);
      add(results, option, 0, 0, 0, 6);
      option.setOnClickListener(
          v -> {
            destination = a;
            showDestination();
            hideKeyboard();
            if (action != 0) prepare(action);
          });
    }
  }

  private void showFavorites(View anchor) {
    hideKeyboard();
    if (favoritesDialog != null) favoritesDialog.dismiss();
    favoritesDialog =
        new SavedPlacesDialog(
            this,
            place ->
                chooseExact(place.name + " · " + place.address, place.latitude, place.longitude));
    favoritesDialog.show();
  }

  private void chooseExact(String label, double lat, double lon) {
    label = label.length() > 400 ? label.substring(0, 400) : label;
    query.setText(label.contains(" · ") ? label.substring(0, label.indexOf(" · ")) : label);
    query.setSelection(query.length());
    if (suggest != null) handler.removeCallbacks(suggest);
    destination = new Address(Locale.getDefault());
    destination.setLatitude(lat);
    destination.setLongitude(lon);
    destination.setAddressLine(0, label);
    showDestination();
    hideKeyboard();
  }

  private void openMaps() {
    try {
      startActivity(
          new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
              .setPackage("com.google.android.apps.maps"));
    } catch (Exception e) {
      try {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps")));
      } catch (Exception ignored) {
        say("未找到地图应用。也可输入或粘贴地址。");
      }
    }
  }

  private void receiveShare(Intent intent) {
    if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
    if (favoritesDialog != null) favoritesDialog.dismiss();
    CharSequence extra = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
    if ((extra == null || extra.toString().trim().isEmpty())
        && intent.getClipData() != null
        && intent.getClipData().getItemCount() > 0)
      extra = intent.getClipData().getItemAt(0).coerceToText(this);
    if (extra == null || extra.toString().trim().isEmpty()) {
      say("没有收到地点文字或链接，请从 Maps 的地点详情页分享。");
      return;
    }
    // A new Maps share always starts a new route from the user's current location.
    if (locationCancel != null) {
      locationCancel.cancel();
      locationCancel = null;
    }
    locating = false;
    pendingAction = 0;
    locate(0);
    importPlace(extra.toString());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    receiveShare(intent);
  }

  private void pasteAddress() {
    android.content.ClipboardManager cm =
        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    if (!cm.hasPrimaryClip()
        || cm.getPrimaryClip() == null
        || cm.getPrimaryClip().getItemCount() == 0) {
      say("剪贴板没有文字。也可从 Maps 分享地点到这里。");
      return;
    }
    CharSequence content = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
    if (content == null || content.toString().trim().isEmpty()) {
      say("剪贴板没有可用地址。");
      return;
    }
    importPlace(content.toString());
  }

  private void importPlace(String value) {
    String raw = value.trim();
    if (raw.length() > 12000) {
      say("分享内容过长，请只分享一个地点。");
      return;
    }
    query.setText("");
    if (suggest != null) handler.removeCallbacks(suggest);
    final int token = ++importGeneration;
    if (MapsShare.firstUrl(raw) == null) {
      query.setText(raw);
      if (suggest != null) handler.removeCallbacks(suggest);
      search(0);
      return;
    }
    importing = true;
    updateBusy();
    status.setVisibility(View.GONE);
    worker.execute(
        () -> {
          MapsShare.Result found = null;
          String failure = null;
          try {
            found = MapsShare.resolve(raw);
          } catch (Exception e) {
            failure = e.getMessage();
            if (failure == null || failure.trim().isEmpty()) failure = e.getClass().getSimpleName();
          }
          final MapsShare.Result resolved = found;
          final String importError = failure;
          handler.post(
              () -> {
                if (!alive() || token != importGeneration) return;
                importing = false;
                updateBusy();
                if (resolved == null) {
                  say(
                      "无法带入地点："
                          + (importError == null ? "没有找到地点信息" : importError)
                          + "。可复制地址文字后 Paste。");
                  return;
                }
                if (resolved.exact()) {
                  String label =
                      resolved.text == null || resolved.text.isEmpty()
                          ? String.format(Locale.US, "地图地点 %.5f, %.5f", resolved.lat, resolved.lon)
                          : resolved.text;
                  chooseExact(label, resolved.lat, resolved.lon);
                  status.setVisibility(View.GONE);
                } else {
                  query.setText(resolved.text);
                  if (suggest != null) handler.removeCallbacks(suggest);
                  search(0);
                }
              });
        });
  }

  private void hideKeyboard() {
    View focus = getCurrentFocus();
    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(query.getWindowToken(), 0);
    if (focus != null) focus.clearFocus();
  }

  private void search(int after) {
    search(after, false);
  }

  private void search(int after, boolean quiet) {
    if (suggest != null) handler.removeCallbacks(suggest);
    String value = query.getText().toString().trim();
    if (value.isEmpty()) {
      if (!quiet) {
        say("先输入、粘贴或从 Maps 分享目的地。");
        query.requestFocus();
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
            .showSoftInput(query, InputMethodManager.SHOW_IMPLICIT);
      }
      return;
    }
    if (value.matches("(?s).*https?://.*")) {
      if (!quiet) importPlace(value);
      return;
    }
    if (searching) {
      if (after != 0) {
        pendingSearchAction = after;
        backgroundSearch = false;
        updateBusy();
        hideKeyboard();
        status.setVisibility(View.GONE);
      }
      return;
    }
    searching = true;
    destination = null;
    chosenRow.setVisibility(View.GONE);
    backgroundSearch = quiet;
    final int generation = queryGeneration;
    results.removeAllViews();
    results.setVisibility(View.GONE);
    if (!quiet) status.setVisibility(View.GONE);
    updateBusy();
    worker.execute(
        () -> {
          List<Address> found = null;
          String error = null;
          try {
            if (!Geocoder.isPresent()) throw new Exception();
            found = new Geocoder(this, Locale.getDefault()).getFromLocationName(value, 5);
          } catch (Exception e) {
            error = "地址查询暂不可用。可以从 Google Maps 分享地点，或稍后重试。";
          }
          final List<Address> addresses = found;
          final String problem = error;
          handler.post(
              () -> {
                if (!alive() || generation != queryGeneration) return;
                searching = false;
                final int action = pendingSearchAction != 0 ? pendingSearchAction : after;
                pendingSearchAction = 0;
                updateBusy();
                if (problem != null) {
                  say(problem);
                  return;
                }
                if (addresses == null || addresses.isEmpty()) {
                  say("没有找到匹配地点。");
                  return;
                }
                showCandidates(addresses, action);
              });
        });
  }

  private void showMenu(View anchor) {
    PopupMenu menu = new PopupMenu(this, anchor);
    menu.getMenu().add("打开 Google Maps");
    menu.getMenu().add("只打开 Uber");
    menu.getMenu().add("只打开 Lyft");
    menu.getMenu().add("查看上车点坐标");
    menu.getMenu().add("帮助与隐私");
    menu.setOnMenuItemClickListener(
        item -> {
          String title = item.getTitle().toString();
          if (title.equals("打开 Google Maps")) openMaps();
          else if (title.equals("只打开 Uber")) prepare(1);
          else if (title.equals("只打开 Lyft")) prepare(2);
          else if (title.equals("查看上车点坐标"))
            new AlertDialog.Builder(this)
                .setTitle("上车点")
                .setMessage(
                    origin == null
                        ? "尚未定位"
                        : String.format(
                            Locale.US,
                            "%.6f, %.6f\n精度约 %.0f 米",
                            origin.getLatitude(),
                            origin.getLongitude(),
                            origin.getAccuracy()))
                .setPositiveButton("完成", null)
                .show();
          else
            new AlertDialog.Builder(this)
                .setTitle("Ride Compare · 0.14")
                .setMessage(
                    "1. 从 Google Maps 分享地点可自动填入目的地；也可手动输入或粘贴后选中 ✓。\n"
                        + "2. 上车点默认当前位置，也可输入地址，选中结果。\n"
                        + "3. 展开内屏，从本工具全屏状态点击“比较价格”。\n\n"
                        + "Uber 可能需要确认上车点。分屏失败时，可从此菜单分别打开，再手动分屏。\n\n"
                        + "常用地址可搜索地点、确认后命名保存；仅保存在本机，删除应用或清除数据后移除。仅在点击 Paste"
                        + " 时读取剪贴板。地址查询使用系统地理编码服务；实时位置仅用于本次传递，不保存行程、账号或价格，不自动叫车。\n\n"
                        + "分享链接可能需要联网解析；链接无法识别时，可复制地址文字。")
                .setPositiveButton("知道了", null)
                .show();
          return true;
        });
    menu.show();
  }

  private void prepare(int action) {
    if (destination == null) {
      search(action);
      return;
    }
    if (action == 3 && (!installed(UBER) || !installed(LYFT))) {
      say("同时打开需要已安装 Uber 和 Lyft。");
      return;
    }
    if (pickupState.manual() && origin == null) {
      pickupQuery.requestFocus();
      searchPickup();
      return;
    }
    if (pickupState.needsGps(
        origin != null,
        origin == null
            ? 0
            : SystemClock.elapsedRealtimeNanos() - origin.getElapsedRealtimeNanos())) {
      locate(action);
      return;
    }
    launch(action);
  }

  private boolean installed(String pkg) {
    try {
      getPackageManager().getApplicationInfo(pkg, 0);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private void searchPickup() {
    if (pickupSuggest != null) handler.removeCallbacks(pickupSuggest);
    String value = pickupQuery.getText().toString().trim();
    if (value.isEmpty()) {
      pickupError.setText("输入上车地址，或选择当前位置。");
      pickupError.setVisibility(View.VISIBLE);
      return;
    }
    if (pickupSearching) return;
    pickupSearching = true;
    final int generation = pickupGeneration;
    pickupError.setVisibility(View.GONE);
    pickupResults.removeAllViews();
    pickupResults.setVisibility(View.GONE);
    updateBusy();
    worker.execute(
        () -> {
          List<Address> found = null;
          try {
            if (Geocoder.isPresent())
              found = new Geocoder(this, Locale.getDefault()).getFromLocationName(value, 5);
          } catch (Exception ignored) {
          }
          final List<Address> addresses = found;
          handler.post(
              () -> {
                if (!alive() || generation != pickupGeneration || !pickupState.manual()) return;
                pickupSearching = false;
                updateBusy();
                if (addresses == null || addresses.isEmpty()) {
                  pickupError.setText("没有找到上车地址，请检查网络或补充城市。");
                  pickupError.setVisibility(View.VISIBLE);
                  return;
                }
                pickupResults.setVisibility(View.VISIBLE);
                for (Address a : addresses) {
                  LinearLayout option = row();
                  option.setGravity(Gravity.CENTER_VERTICAL);
                  option.setPadding(dp(12), dp(12), dp(12), dp(12));
                  option.setBackground(
                      new RippleDrawable(
                          ColorStateList.valueOf(0x33713DE8),
                          shape(0xFF292633, 14, 0xFF494054),
                          shape(Color.WHITE, 14, 0)));
                  option.setClickable(true);
                  option.setFocusable(true);
                  option.setContentDescription(addressText(a) + "，选为上车点");
                  TextView label = text(addressText(a), 14, INK);
                  label.setMaxLines(3);
                  label.setEllipsize(TextUtils.TruncateAt.END);
                  option.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
                  TextView tick = text("✓", 20, PURPLE);
                  tick.setGravity(Gravity.CENTER);
                  LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(40));
                  lp.leftMargin = dp(8);
                  option.addView(tick, lp);
                  add(pickupResults, option, 0, 0, 0, 6);
                  option.setOnClickListener(
                      v -> {
                        setManualPickup(a.getLatitude(), a.getLongitude(), addressText(a));
                        hideKeyboard();
                      });
                }
              });
        });
  }

  private void setManualPickup(double lat, double lon, String label) {
    pickupState.editManual();
    pickupGeneration++;
    launchGeneration++;
    splitPending = false;
    if (pickupSuggest != null) handler.removeCallbacks(pickupSuggest);
    if (locationCancel != null) {
      locationCancel.cancel();
      locationCancel = null;
    }
    locating = false;
    pendingAction = 0;
    origin = new Location("manual");
    origin.setLatitude(lat);
    origin.setLongitude(lon);
    origin.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
    origin.setTime(System.currentTimeMillis());
    pickupAddress = label;
    settingPickupText = true;
    pickupQuery.setText(label);
    settingPickupText = false;
    pickupQuery.setHint("输入上车地址");
    pickup.setVisibility(View.GONE);
    pickupCheck.setVisibility(View.VISIBLE);
    locateButton.setText("当前位置");
    locateButton.setEnabled(true);
    pickupSearching = false;
    pickupResults.removeAllViews();
    pickupResults.setVisibility(View.GONE);
    pickupError.setVisibility(View.GONE);
    status.setVisibility(View.GONE);
    updateBusy();
  }

  private void locate(int next) {
    if (locating) {
      if (next != 0) pendingAction = next;
      status.setVisibility(View.GONE);
      return;
    }
    final int revision = pickupState.useCurrent();
    launchGeneration++;
    splitPending = false;
    pickupGeneration++;
    pickupSearching = false;
    if (pickupSuggest != null) handler.removeCallbacks(pickupSuggest);
    pickupResults.removeAllViews();
    pickupResults.setVisibility(View.GONE);
    pickupError.setVisibility(View.GONE);
    pickupCheck.setVisibility(View.GONE);
    pickupAddress = "当前位置";
    settingPickupText = true;
    pickupQuery.setText("");
    settingPickupText = false;
    pickupQuery.setHint("当前位置");
    pickup.setVisibility(View.VISIBLE);
    origin = null;
    updateBusy();
    pendingAction = next;
    if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(
          new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
          },
          10);
      return;
    }
    LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
    if (!lm.isLocationEnabled()) {
      say("手机定位已关闭，请打开定位后再试。");
      return;
    }
    locating = true;
    origin = null;
    locateButton.setText("…");
    locateButton.setEnabled(false);
    updateBusy();
    pickup.setText("…");
    status.setVisibility(View.GONE);
    locationCancel = new CancellationSignal();
    final CancellationSignal request = locationCancel;
    String provider =
        lm.getAllProviders().contains(LocationManager.FUSED_PROVIDER)
            ? LocationManager.FUSED_PROVIDER
            : LocationManager.NETWORK_PROVIDER;
    try {
      lm.getCurrentLocation(
          provider,
          request,
          getMainExecutor(),
          location -> {
            if (locationCancel != request
                || !alive()
                || !locating
                || !pickupState.acceptsGps(revision)) return;
            locating = false;
            locateButton.setText("重试");
            locateButton.setEnabled(true);
            updateBusy();
            if (location == null) {
              pickup.setText("定位失败");
              say("未获得位置，请到信号较好的地方重试。");
              return;
            }
            if (SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos()
                > 120_000_000_000L) {
              say("返回的位置过旧，请重新定位。");
              pickup.setText("需要刷新位置");
              return;
            }
            origin = location;
            locateButton.setText("刷新");
            pickup.setText(String.format(Locale.CHINA, "± %.0f 米", origin.getAccuracy()));
            pickupCheck.setVisibility(View.VISIBLE);
            updateBusy();
            if (pendingAction != 0) {
              int action = pendingAction;
              pendingAction = 0;
              launch(action);
            }
          });
      handler.postDelayed(
          () -> {
            if (locating && locationCancel == request) {
              request.cancel();
              locating = false;
              locateButton.setText("重试");
              locateButton.setEnabled(true);
              updateBusy();
              pickup.setText("定位超时");
              say("定位超时。请确认定位权限和网络，然后重试。");
            }
          },
          15000);
    } catch (Exception e) {
      locating = false;
      locateButton.setText("重试");
      locateButton.setEnabled(true);
      updateBusy();
      pickup.setText("定位不可用");
      say("无法定位，请检查系统定位设置后重试。");
    }
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(request, permissions, grants);
    if (request == 10) {
      if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
          == PackageManager.PERMISSION_GRANTED) {
        if (!pickupState.manual()) locate(pendingAction);
      } else say("需要位置权限，才能把相同上车点交给两家。请允许使用时访问位置。");
    }
  }

  private Uri rideUri(boolean uber) {
    Uri.Builder b =
        new Uri.Builder()
            .scheme(uber ? "uber" : "lyft")
            .authority(uber ? "riderequest" : "ridetype");
    if (uber) {
      b.appendQueryParameter("action", "setPickup");
      b.appendQueryParameter("pickup[latitude]", Double.toString(origin.getLatitude()));
      b.appendQueryParameter("pickup[longitude]", Double.toString(origin.getLongitude()));
      b.appendQueryParameter("pickup[nickname]", pickupAddress);
      if (pickupState.manual()) b.appendQueryParameter("pickup[formatted_address]", pickupAddress);
      b.appendQueryParameter("dropoff[latitude]", Double.toString(destination.getLatitude()));
      b.appendQueryParameter("dropoff[longitude]", Double.toString(destination.getLongitude()));
      b.appendQueryParameter("dropoff[nickname]", addressText(destination));
      b.appendQueryParameter("dropoff[formatted_address]", addressText(destination));
    } else {
      b.appendQueryParameter("id", "lyft");
      if (pickupState.manual()) b.appendQueryParameter("pickup[address]", pickupAddress);
      b.appendQueryParameter("pickup[latitude]", Double.toString(origin.getLatitude()));
      b.appendQueryParameter("pickup[longitude]", Double.toString(origin.getLongitude()));
      b.appendQueryParameter("destination[latitude]", Double.toString(destination.getLatitude()));
      b.appendQueryParameter("destination[longitude]", Double.toString(destination.getLongitude()));
    }
    return b.build();
  }

  private boolean open(boolean uber, boolean adjacent) {
    String pkg = uber ? UBER : LYFT;
    Intent i =
        new Intent(Intent.ACTION_VIEW, rideUri(uber))
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    if (adjacent) i.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
    try {
      startActivity(i);
      return true;
    } catch (ActivityNotFoundException e) {
      say((uber ? "Uber" : "Lyft") + " 未安装，或当前版本不接受此链接。");
      return false;
    } catch (Exception e) {
      say("系统未允许打开应用，请分别测试。");
      return false;
    }
  }

  private void launch(int action) {
    if (action == 1) {
      open(true, false);
      return;
    }
    if (action == 2) {
      open(false, false);
      return;
    }
    if (isInMultiWindowMode()) {
      new AlertDialog.Builder(this)
          .setTitle("请从全屏启动")
          .setMessage("请先把本工具恢复为全屏，再点同时打开。也可以分别打开两家后手动分屏。")
          .setPositiveButton("知道了", null)
          .show();
      return;
    }
    status.setVisibility(View.GONE);
    splitPending = true;
    splitStarted = SystemClock.elapsedRealtime();
    if (!open(true, true)) {
      splitPending = false;
      return;
    }
    handler.postDelayed(
        () -> {
          if (!alive()) return;
          if (splitPending) {
            splitPending = false;
            say("未检测到分屏。请分别打开两家，再用三星应用对或最近任务分屏。");
          }
        },
        6000);
  }

  @Override
  public void onMultiWindowModeChanged(boolean multi, android.content.res.Configuration config) {
    super.onMultiWindowModeChanged(multi, config);
    if (multi && splitPending && SystemClock.elapsedRealtime() - splitStarted < 6000) {
      splitPending = false;
      final int token = launchGeneration;
      handler.postDelayed(
          () -> {
            if (alive()
                && isInMultiWindowMode()
                && token == launchGeneration
                && destination != null
                && origin != null) {
              status.setVisibility(View.GONE);
              open(false, false);
            }
          },
          900);
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    state.putString("query", query.getText().toString());
    state.putBoolean("manualPickup", pickupState.manual());
    state.putString("pickupQuery", pickupQuery.getText().toString());
    if (pickupState.manual() && origin != null) {
      state.putDouble("pickupLat", origin.getLatitude());
      state.putDouble("pickupLon", origin.getLongitude());
      state.putString("pickupAddress", pickupAddress);
    }
    if (destination != null) {
      state.putDouble("lat", destination.getLatitude());
      state.putDouble("lon", destination.getLongitude());
      state.putString("address", addressText(destination));
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (favoritesDialog != null) favoritesDialog.dismiss();
    if (locationCancel != null) locationCancel.cancel();
    importGeneration++;
    handler.removeCallbacksAndMessages(null);
    worker.shutdownNow();
  }
}
