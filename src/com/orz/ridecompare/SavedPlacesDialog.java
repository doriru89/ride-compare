package com.orz.ridecompare;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A single dialog for choosing a favorite or confirming and naming a new address. */
final class SavedPlacesDialog extends Dialog {
  interface Listener {
    void onChoose(SavedPlaces.Place place);
  }

  private static final int INK = 0xFFF4F1FA, MUTED = 0xFFABA6B8, PURPLE = 0xFFB18AFF;
  private final Activity activity;
  private final Listener listener;
  private final SavedPlacesStore store;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newFixedThreadPool(2);
  private final LinearLayout body;
  private final TextView title, addButton;
  private EditText addressInput, nameInput;
  private TextView error, saveButton, searchButton;
  private LinearLayout candidates, nameSection;
  private SavedPlaces.Place selected;
  private int revision;
  private Runnable pendingSearch;
  private boolean closed, saving;
  private AlertDialog deleteDialog;

  SavedPlacesDialog(Activity activity, Listener listener) {
    super(activity);
    this.activity = activity;
    this.listener = listener;
    store = new SavedPlacesStore(activity);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    LinearLayout panel = column();
    panel.setPadding(dp(24), dp(20), dp(24), dp(20));
    panel.setBackground(
        new NeoDrawable(activity.getResources().getDisplayMetrics().density, false));
    panel.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    LinearLayout header = row();
    title = label("常用地址", 22, INK);
    title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    TextView close = button("×");
    close.setContentDescription("关闭常用地址");
    header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
    close.setOnClickListener(v -> dismiss());
    panel.addView(header);
    ScrollView scroll =
        new ScrollView(activity) {
          @Override
          protected void onMeasure(int width, int height) {
            int cap = (int) (activity.getResources().getDisplayMetrics().heightPixels * .62f);
            if (MeasureSpec.getMode(height) != MeasureSpec.UNSPECIFIED)
              cap = Math.min(cap, MeasureSpec.getSize(height));
            super.onMeasure(width, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
          }
        };
    scroll.setFillViewport(false);
    body = column();
    scroll.addView(body);
    panel.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
    addButton = button("＋ 添加地址");
    panel.addView(addButton, new LinearLayout.LayoutParams(-1, dp(48)));
    addButton.setOnClickListener(v -> showEditor());
    setContentView(panel);
    setOnDismissListener(
        d -> {
          closed = true;
          if (deleteDialog != null) deleteDialog.dismiss();
          revision++;
          handler.removeCallbacksAndMessages(null);
          worker.shutdownNow();
        });
    showList();
  }

  @Override
  public void show() {
    super.show();
    Window window = getWindow();
    if (window != null) {
      window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
      window.setDimAmount(.6f);
      window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      window.setSoftInputMode(
          WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
              | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
      window.setLayout(
          Math.min(dp(560), activity.getResources().getDisplayMetrics().widthPixels - dp(32)), -2);
    }
  }

  private void showList() {
    revision++;
    if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
    body.removeAllViews();
    title.setText("常用地址");
    addButton.setVisibility(View.VISIBLE);
    for (SavedPlaces.Place place : store.load().all()) {
      LinearLayout item = row();
      item.setPadding(dp(4), dp(8), dp(4), dp(8));
      LinearLayout details = column();
      details.setPadding(dp(8), dp(8), dp(8), dp(8));
      details.setBackground(ripple());
      details.setClickable(true);
      details.setFocusable(true);
      details.setContentDescription(place.name + "，" + place.address);
      TextView name = label(place.name, 18, INK);
      name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
      details.addView(name);
      TextView address = label(place.address, 13, MUTED);
      address.setMaxLines(2);
      address.setEllipsize(TextUtils.TruncateAt.END);
      details.addView(address);
      item.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
      details.setOnClickListener(
          v -> {
            dismiss();
            listener.onChoose(place);
          });
      TextView delete = button("×");
      delete.setContentDescription("删除常用地址 " + place.name);
      item.addView(delete, new LinearLayout.LayoutParams(dp(48), dp(48)));
      delete.setOnClickListener(
          v ->
              deleteDialog =
                  new AlertDialog.Builder(activity)
                      .setTitle("删除“" + place.name + "”？")
                      .setNegativeButton("取消", null)
                      .setPositiveButton(
                          "删除",
                          (d, which) -> {
                            if (closed) return;
                            SavedPlaces places = store.load();
                            for (SavedPlaces.Place existing : places.all())
                              if (existing.name.equals(place.name)) {
                                places.remove(existing);
                                break;
                              }
                            if (store.save(places)) showList();
                            else
                              android.widget.Toast.makeText(
                                      activity, "保存失败，请重试", android.widget.Toast.LENGTH_SHORT)
                                  .show();
                          })
                      .show());
      body.addView(item);
    }
  }

  private void showEditor() {
    revision++;
    selected = null;
    body.removeAllViews();
    title.setText("添加常用地址");
    addButton.setVisibility(View.GONE);
    LinearLayout inputRow = row();
    addressInput = input("输入地址", 1200);
    addressInput.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
    addressInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    addressInput.setContentDescription("搜索常用地址");
    inputRow.addView(addressInput, new LinearLayout.LayoutParams(0, -2, 1));
    searchButton = button("搜索");
    inputRow.addView(searchButton, new LinearLayout.LayoutParams(dp(64), dp(48)));
    body.addView(inputRow);
    error = label("", 13, 0xFFFFB8C8);
    error.setVisibility(View.GONE);
    body.addView(error);
    candidates = column();
    body.addView(candidates);
    nameSection = column();
    nameSection.setVisibility(View.GONE);
    nameSection.addView(label("名称", 13, MUTED));
    nameInput = input("例如 work、home", 40);
    nameInput.setSingleLine(true);
    nameInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
    nameSection.addView(nameInput);
    saveButton = button("保存");
    nameSection.addView(saveButton, new LinearLayout.LayoutParams(-1, dp(48)));
    body.addView(nameSection);
    TextView back = button("返回常用地址");
    body.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));
    back.setOnClickListener(
        v -> {
          hideKeyboard();
          showList();
        });
    searchButton.setOnClickListener(v -> search());
    addressInput.setOnEditorActionListener(
        (v, action, event) -> {
          if (action == EditorInfo.IME_ACTION_SEARCH) {
            search();
            return true;
          }
          return false;
        });
    nameInput.setOnEditorActionListener(
        (v, action, event) -> {
          if (action == EditorInfo.IME_ACTION_DONE) {
            save();
            return true;
          }
          return false;
        });
    saveButton.setOnClickListener(v -> save());
    addressInput.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            revision++;
            selected = null;
            nameSection.setVisibility(View.GONE);
            candidates.removeAllViews();
            error.setVisibility(View.GONE);
            searchButton.setText("搜索");
            if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
            if (s.toString().trim().length() >= 2) {
              pendingSearch = () -> search();
              handler.postDelayed(pendingSearch, 650);
            }
          }

          public void afterTextChanged(Editable text) {}
        });
  }

  private void search() {
    if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
    String value = addressInput.getText().toString().trim();
    if (value.isEmpty()) {
      fail("请输入地址");
      return;
    }
    final int token = ++revision;
    selected = null;
    nameSection.setVisibility(View.GONE);
    candidates.removeAllViews();
    error.setVisibility(View.GONE);
    searchButton.setText("…");
    worker.execute(
        () -> {
          List<Address> found = new ArrayList<>();
          String problem = null;
          try {
            if (MapsShare.firstUrl(value) != null) {
              MapsShare.Result result = MapsShare.resolve(value);
              if (result.exact()) {
                Address address = new Address(Locale.getDefault());
                address.setLatitude(result.lat);
                address.setLongitude(result.lon);
                address.setAddressLine(
                    0, result.text.isEmpty() ? result.lat + ", " + result.lon : result.text);
                found.add(address);
              }
              if (!result.exact()) found = geocode(result.text);
            } else found = geocode(value);
          } catch (Exception e) {
            problem = "地址查询失败，请稍后重试";
          }
          final List<Address> results = found;
          final String message = problem;
          handler.post(
              () -> {
                if (closed || token != revision) return;
                searchButton.setText("搜索");
                if (message != null) {
                  fail(message);
                  return;
                }
                if (results == null || results.isEmpty()) {
                  fail("没有找到匹配地点");
                  return;
                }
                for (Address address : results) {
                  if (!address.hasLatitude() || !address.hasLongitude()) continue;
                  String formatted =
                      address.getMaxAddressLineIndex() >= 0
                          ? address.getAddressLine(0)
                          : address.getLatitude() + ", " + address.getLongitude();
                  final SavedPlaces.Place candidatePlace;
                  try {
                    candidatePlace =
                        new SavedPlaces.Place(
                            "selected", formatted, address.getLatitude(), address.getLongitude());
                  } catch (IllegalArgumentException ignored) {
                    continue;
                  }
                  TextView candidate = button(formatted + "  ✓");
                  candidate.setGravity(Gravity.CENTER_VERTICAL);
                  candidate.setPadding(dp(12), dp(12), dp(12), dp(12));
                  candidate.setTextColor(INK);
                  candidate.setContentDescription(formatted + "，确认常用地址");
                  candidates.addView(candidate, new LinearLayout.LayoutParams(-1, -2));
                  candidate.setOnClickListener(
                      v -> {
                        revision++;
                        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                        selected = candidatePlace;
                        candidates.removeAllViews();
                        TextView confirmed = label(formatted + "  ✓", 15, PURPLE);
                        confirmed.setPadding(dp(8), dp(12), dp(8), dp(12));
                        candidates.addView(confirmed);
                        nameSection.setVisibility(View.VISIBLE);
                        nameInput.requestFocus();
                      });
                }
                if (candidates.getChildCount() == 0) fail("没有找到有效地点");
              });
        });
  }

  private List<Address> geocode(String text) throws Exception {
    if (!Geocoder.isPresent()) throw new IllegalStateException("Geocoder unavailable");
    return new Geocoder(activity, Locale.getDefault()).getFromLocationName(text, 5);
  }

  private void save() {
    if (saving || selected == null) return;
    try {
      SavedPlaces places = store.load();
      SavedPlaces.Place place =
          new SavedPlaces.Place(
              nameInput.getText().toString(),
              selected.address,
              selected.latitude,
              selected.longitude);
      places.add(place);
      saving = true;
      if (!store.save(places)) {
        fail("保存失败，请重试");
        return;
      }
      hideKeyboard();
      showList();
    } catch (IllegalArgumentException e) {
      fail(e.getMessage());
    } finally {
      saving = false;
    }
  }

  private void fail(String message) {
    error.setText(message);
    error.setVisibility(View.VISIBLE);
  }

  private void hideKeyboard() {
    View focused = getCurrentFocus();
    if (focused != null)
      ((InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE))
          .hideSoftInputFromWindow(focused.getWindowToken(), 0);
  }

  private int dp(float value) {
    return Math.round(value * activity.getResources().getDisplayMetrics().density);
  }

  private LinearLayout column() {
    LinearLayout view = new LinearLayout(activity);
    view.setOrientation(LinearLayout.VERTICAL);
    return view;
  }

  private LinearLayout row() {
    LinearLayout view = new LinearLayout(activity);
    view.setGravity(Gravity.CENTER_VERTICAL);
    return view;
  }

  private TextView label(String text, int size, int color) {
    TextView view = new TextView(activity);
    view.setText(text);
    view.setTextSize(size);
    view.setTextColor(color);
    view.setIncludeFontPadding(false);
    return view;
  }

  private android.graphics.drawable.Drawable ripple() {
    GradientDrawable shape = new GradientDrawable();
    shape.setColor(0xFF30283F);
    shape.setCornerRadius(dp(14));
    return new RippleDrawable(ColorStateList.valueOf(0x33713DE8), shape, null);
  }

  private TextView button(String text) {
    TextView view = label(text, 15, PURPLE);
    view.setGravity(Gravity.CENTER);
    view.setMinHeight(dp(48));
    view.setPadding(dp(8), dp(6), dp(8), dp(6));
    view.setBackground(ripple());
    view.setClickable(true);
    view.setFocusable(true);
    return view;
  }

  private EditText input(String hint, int maxLength) {
    EditText view = new EditText(activity);
    view.setTextColor(INK);
    view.setHintTextColor(MUTED);
    view.setHint(hint);
    view.setTextSize(17);
    view.setMinHeight(dp(56));
    view.setFilters(new InputFilter[] {new InputFilter.LengthFilter(maxLength)});
    return view;
  }
}
