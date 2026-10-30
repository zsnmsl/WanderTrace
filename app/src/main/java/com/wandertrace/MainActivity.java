package com.wandertrace;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.util.Linkify;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.AdapterView;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String MAP_PROVIDER = "map_provider";
    private static final String SETUP_DONE = "map_setup_complete";
    private static final String EDITOR_DRAFT = "editor_draft";
    private SharedPreferences prefs;
    private Config cfg, active;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService maintenance = Executors.newSingleThreadExecutor();
    private EditText etKey, etAccuracy, etSearch, etJitter;
    private RadioGroup rgMode, rgProvider, rgMapLayer;
    private RadioButton rbSingle, rbRoute, rbSettings, rbOsm, rbAmap, rbTianditu,rbTencent,rbBaidu;
    private LinearLayout boxRoute, boxSettings;
    private Switch swWgs84;
    private Button btnSaveKey, btnSearch, btnStart, btnAddPoint;
    private TextView tvStatus, tvPlanInfo, tvPreview, tvSelection, tvMapHint, tvKeyHint;
    private RouteMapView routeMap;
    private boolean changingProvider, systemReady;
    private int mapRevision;
    private AlertDialog setupDialog,routeDialog;
    private RouteStore routeStore;
    private boolean applying;
    private boolean initializing=true;
    private EnvironmentStatus environment;
    private boolean environmentChecking;
    private TextView tvEnvironment;
    private boolean hotReloading;
    private boolean automaticReloadAttempted,recheckPending;
    private String reloadNote="";
    private boolean changingLayer;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE);
        routeStore=new RouteStore(prefs);
        HotReloadController.initialize();
        active = Config.load(this);
        cfg = Config.fromJson(prefs.getString(EDITOR_DRAFT, active.toJson()));
        bind(); fill(); ui.post(tick);
        if (MapSetup.required(prefs)) showMapSetup(true);
        else restoreProvider();
    }
    @Override protected void onStart() {super.onStart();automaticReloadAttempted=false;checkEnvironment();}
    @Override protected void onPause() {saveDraft();super.onPause();}
    @Override protected void onDestroy() { ui.removeCallbacks(tick); if (setupDialog != null) setupDialog.dismiss(); if (routeDialog != null) routeDialog.dismiss(); io.shutdownNow(); maintenance.shutdownNow(); super.onDestroy(); }

    private void bind() {
        etKey = findViewById(R.id.etKey);
        etAccuracy = findViewById(R.id.etAccuracy); etSearch = findViewById(R.id.etSearch);
        etJitter = findViewById(R.id.etJitter);
        rgMode = findViewById(R.id.rgMode);
        rgProvider = findViewById(R.id.rgProvider);
        rgMapLayer=findViewById(R.id.rgMapLayer);
        rbSingle = findViewById(R.id.rbSingle); rbRoute = findViewById(R.id.rbRoute);
        rbSettings = findViewById(R.id.rbSettings); rbTianditu = findViewById(R.id.rbTianditu);
        rbOsm = findViewById(R.id.rbOsm); rbAmap = findViewById(R.id.rbAmap);
        rbTencent=findViewById(R.id.rbTencent);rbBaidu=findViewById(R.id.rbBaidu);
        boxRoute = findViewById(R.id.boxRoute); boxSettings = findViewById(R.id.boxSettings);
        swWgs84 = findViewById(R.id.swWgs84);
        routeMap = findViewById(R.id.routeMap);
        btnSaveKey = findViewById(R.id.btnSaveKey); btnSearch = findViewById(R.id.btnSearch);
        btnStart = findViewById(R.id.btnStart); btnAddPoint=findViewById(R.id.btnAddPoint);
        tvStatus = findViewById(R.id.tvStatus); tvPlanInfo = findViewById(R.id.tvPlanInfo);
        tvPreview = findViewById(R.id.tvPreview);
        tvSelection = findViewById(R.id.tvSelection); tvMapHint = findViewById(R.id.tvMapHint); tvKeyHint = findViewById(R.id.tvKeyHint);
        tvEnvironment = findViewById(R.id.tvEnvironment);
        tvEnvironment.setOnClickListener(v -> showEnvironmentDetail());

        rgMode.setOnCheckedChangeListener((g, id) -> {
            if (id == R.id.rbSettings) { showPage(true); return; }
            cfg.mode = id == R.id.rbRoute ? Config.MODE_ROUTE : Config.MODE_SINGLE;
            mapRevision++; showPage(false); updateModeUi(); syncPoints(); saveDraft();
        });
        rgProvider.setOnCheckedChangeListener((g, id) -> {
            if (changingProvider) return;
            chooseProvider(id==R.id.rbAmap ? "amap" : id==R.id.rbTianditu ? "tianditu" : id==R.id.rbTencent ? "tencent" : id==R.id.rbBaidu ? "baidu" : "osm");
        });
        rgMapLayer.setOnCheckedChangeListener((group,id) -> {
            if(changingLayer) return;
            String provider=prefs.getString(MAP_PROVIDER,"osm");
            boolean satellite=id==R.id.rbSatellite;
            if(satellite && !MapLayers.supportsSatellite(provider)) {updateLayerUi(provider);return;}
            MapLayers.save(prefs,provider,satellite);
            routeMap.configureTiles(provider,MapProviders.key(prefs,provider),satellite);
        });
        findViewById(R.id.btnUndo).setOnClickListener(v -> removePoint(false));
        findViewById(R.id.btnClear).setOnClickListener(v -> removePoint(true));
        findViewById(R.id.btnSaveRoute).setOnClickListener(v -> saveRoute());
        findViewById(R.id.btnLoadRoute).setOnClickListener(v -> loadRoute());
        findViewById(R.id.btnMapSetup).setOnClickListener(v -> showMapSetup(false));
        btnSaveKey.setOnClickListener(v -> saveKeys());
        btnSearch.setOnClickListener(v -> doSearch());
        etSearch.setOnEditorActionListener((v, action, event) -> { if (action == EditorInfo.IME_ACTION_SEARCH) { doSearch(); return true; } return false; });
        btnAddPoint.setOnClickListener(v -> {double[] point=mapCenter();if(point!=null) addMapPoint(point[0],point[1]);});
        btnStart.setOnClickListener(v -> {
            if(active.enabled) stopSpoof();
            else if(cfg.mode==Config.MODE_ROUTE) showRouteOptions();
            else startSingle();
        });
    }

    private void fill() {
        etAccuracy.setText(num(cfg.accuracy));etJitter.setText(num(cfg.jitterMeters));swWgs84.setChecked(cfg.wgs84);
        rgMode.check(cfg.mode == Config.MODE_ROUTE ? R.id.rbRoute : R.id.rbSingle);
        showPage(false); updateModeUi(); syncPoints();
        selectProvider(prefs.getString(MAP_PROVIDER, "osm"));
        double[][] points = currentPoints();
        if (points.length > 0) routeMap.centerOn(points[0][0], points[0][1]);
        try {
            String[] camera=prefs.getString("editor_camera","").split(",");
            if(camera.length==3) routeMap.setCamera(Double.parseDouble(camera[0]),Double.parseDouble(camera[1]),Float.parseFloat(camera[2]));
        } catch(RuntimeException ignored) {}

        initializing=false;
    }
    private void updateModeUi() {
        boolean route = cfg.mode == Config.MODE_ROUTE;
        boxRoute.setVisibility(route ? View.VISIBLE : View.GONE); routeMap.setRouteMode(route);
        tvMapHint.setText(route ? "将中心标记对准节点，再点击添加选点" : "中心标记所在位置就是模拟位置");
        ((TextView)findViewById(R.id.tvPageTitle)).setText(route ? "连成线，沿途皆风景。" : "选一处，开启游迹。");
        ((TextView)findViewById(R.id.tvPageSubtitle)).setText(route ? "移动地图添加途经点，开始时设置速度与步频。" : "拖动地图，将中心标记对准想去的地方。");
        findViewById(R.id.btnUndo).setVisibility(route ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnClear).setVisibility(route ? View.VISIBLE : View.GONE);
        updateActions();
    }
    private double[][] currentPoints() {
        if (cfg.mode == Config.MODE_ROUTE) return cfg.points == null ? new double[0][] : cfg.points;
        return cfg.singlePointSelected ? new double[][]{{cfg.lat, cfg.lon}} : new double[0][];
    }
    private void syncPoints() {
        double[][] points = currentPoints();
        routeMap.setRouteMode(cfg.mode == Config.MODE_ROUTE); routeMap.setPoints(points);
        if (cfg.mode == Config.MODE_ROUTE) {
            double km = RouteEngine.totalDistance(points) / 1000.0;
            String info = points.length < 2 ? "已添加 " + points.length + " 个节点 · 至少需要 2 个" : String.format(Locale.US, "%d 个节点 · %.2f km", points.length, km);
            tvSelection.setText(info + "   ·   双指缩放"); tvPlanInfo.setText(info);
        } else tvSelection.setText("以地图中心为准   ·   双指缩放查看细节");
    }
    private void addMapPoint(double lat, double lon) {
        if (cfg.mode == Config.MODE_SINGLE) { cfg.lat = lat; cfg.lon = lon; cfg.singlePointSelected = true; }
        else {
            double[][] old = currentPoints(), next = new double[old.length + 1][];
            if(old.length>0 && RouteEngine.totalDistance(new double[][]{old[old.length-1],{lat,lon}})<0.2) {toast("请移动地图后再添加下一个选点");return;}
            System.arraycopy(old, 0, next, 0, old.length); next[old.length] = new double[]{lat, lon};
            cfg.points = next; cfg.routeName = "地图绘制";
        }
        mapRevision++; syncPoints(); persistDraft();
    }
    private void removePoint(boolean all) {
        if (cfg.mode == Config.MODE_SINGLE) cfg.singlePointSelected = false;
        else {
            double[][] old = currentPoints(); int size = all ? 0 : Math.max(0, old.length - 1);
            double[][] next = new double[size][]; System.arraycopy(old, 0, next, 0, size); cfg.points = next;
        }
        mapRevision++; syncPoints(); persistDraft();
    }

    private void showPage(boolean settings) {
        findViewById(R.id.pageEditor).setVisibility(settings ? View.GONE : View.VISIBLE);
        findViewById(R.id.pageSettings).setVisibility(settings ? View.VISIBLE : View.GONE);
        findViewById(R.id.editorActions).setVisibility(settings ? View.GONE : View.VISIBLE);
    }
    private void openSettings(boolean open) {
        rgMode.check(open ? R.id.rbSettings : cfg.mode == Config.MODE_ROUTE ? R.id.rbRoute : R.id.rbSingle);
    }
    private void selectProvider(String provider) {
        changingProvider=true;
        rgProvider.check(switch(provider) {case "amap" -> R.id.rbAmap;case "tianditu" -> R.id.rbTianditu;case "tencent" -> R.id.rbTencent;case "baidu" -> R.id.rbBaidu;default -> R.id.rbOsm;});
        changingProvider=false;
        etKey.setText(MapProviders.key(prefs,provider));etKey.setHint(MapProviders.keyHint(provider));
        tvKeyHint.setText(MapProviders.description(provider));updateLayerUi(provider);
    }
    private void updateLayerUi(String provider) {
        changingLayer=true;
        rgMapLayer.check(MapLayers.satellite(prefs,provider) ? R.id.rbSatellite : R.id.rbRoad);
        boolean supported=MapLayers.supportsSatellite(provider);
        findViewById(R.id.rbSatellite).setEnabled(supported);
        findViewById(R.id.rbSatellite).setAlpha(supported ? 1f : 0.45f);
        ((TextView)findViewById(R.id.tvLayerHint)).setText(supported
                ? "道路图与卫星图可随时切换，自动记住当前地图服务的选择。"
                : "OpenStreetMap 仅提供道路图。使用卫星图请切换高德地图或天地图。");
        changingLayer=false;
    }
    private void restoreProvider() {chooseProvider(prefs.getString(MAP_PROVIDER,"amap"));}
    private void chooseProvider(String provider) {
        if(!MapProviders.known(provider)) provider="amap";
        String key=MapProviders.key(prefs,provider);
        if(MapProviders.needsKey(provider) && key.isEmpty()) {showMapSetup(false,provider);return;}
        routeMap.configureTiles(provider,key,MapLayers.satellite(prefs,provider));
        prefs.edit().putString(MAP_PROVIDER,provider).apply();selectProvider(provider);syncPoints();
    }
    private void showMapSetup(boolean first) {showMapSetup(first,prefs.getString(MAP_PROVIDER,"amap"));}
    private void showMapSetup(boolean first,String selectedProvider) {
        if(setupDialog!=null && setupDialog.isShowing()) return;
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(22*getResources().getDisplayMetrics().density);panel.setPadding(pad,pad/2,pad,pad);
        TextView intro=new TextView(this);intro.setText("选择服务商，只填写该服务的一把 Web 服务 Key，无需 Android SDK Key。密钥仅保存在本机。");panel.addView(intro);
        Spinner provider=new Spinner(this);provider.setId(R.id.setupProvider);
        ArrayAdapter<String> options=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,MapProviders.NAMES);
        options.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);provider.setAdapter(options);panel.addView(provider);
        TextView hint=new TextView(this);hint.setTextSize(13);panel.addView(hint);
        EditText key=new EditText(this);key.setId(R.id.setupMapKey);key.setSingleLine(true);key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);panel.addView(key);
        TextView privacy=new TextView(this);privacy.setTextSize(12);panel.addView(privacy);
        CheckBox consent=new CheckBox(this);consent.setId(R.id.setupConsent);consent.setText("我已阅读服务说明与隐私政策，同意启用所选服务");consent.setTextSize(13);panel.addView(consent);
        String[] drafts=new String[MapProviders.IDS.length];for(int i=0;i<drafts.length;i++) drafts[i]=MapProviders.key(prefs,MapProviders.IDS[i]);
        final int[] previous={-1};
        provider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) {}
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                if(previous[0]>=0) drafts[previous[0]]=text(key).trim();previous[0]=position;
                String which=MapProviders.IDS[position];key.setText(drafts[position]);key.setError(null);key.setHint(MapProviders.keyHint(which));
                hint.setText(MapProviders.description(which));consent.setChecked(false);
                privacy.setText(switch(which) {
                    case "tianditu" -> "天地图将收到瓦片范围、搜索词和服务 Key。服务与隐私说明：https://lbs.tianditu.gov.cn/";
                    case "tencent" -> "腾讯将收到瓦片范围、搜索词和 WebService Key。服务说明：https://lbs.qq.com/";
                    case "baidu" -> "百度接收搜索词和 AK，高德接收底图范围。百度服务：https://lbsyun.baidu.com/\n高德隐私：https://lbs.amap.com/pages/privacy/";
                    case "osm" -> "OSM 接收瓦片范围；启用搜索后高德接收搜索词和 Key。https://osmfoundation.org/wiki/Privacy_Policy";
                    default -> "高德接收瓦片范围、搜索词和 Web 服务 Key。隐私：https://lbs.amap.com/pages/privacy/\n公开瓦片接口可能调整，底图可用性独立于 Web 服务 Key。";
                });Linkify.addLinks(privacy,Linkify.WEB_URLS);
            }
        });provider.setSelection(MapProviders.index(selectedProvider));
        ScrollView scroll=new ScrollView(this);scroll.addView(panel);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(first ? "欢迎使用游迹 · 配置地图 API" : "配置地图 API").setView(scroll).setCancelable(!first).setPositiveButton("保存并使用",null);
        if(!first) builder.setNegativeButton("取消",(d,which) -> selectProvider(prefs.getString(MAP_PROVIDER,"amap")))
                .setOnCancelListener(d -> selectProvider(prefs.getString(MAP_PROVIDER,"amap")));
        setupDialog=builder.create();
        setupDialog.setOnShowListener(d -> setupDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String which=MapProviders.IDS[provider.getSelectedItemPosition()],value=text(key).trim();
            if(MapProviders.needsKey(which) && value.isEmpty()) {key.setError("请输入所选服务的 Web 服务 Key");return;}
            if(!consent.isChecked()) {toast("请先阅读并同意所选服务说明");return;}
            MapProviders.saveKey(prefs,which,value);
            prefs.edit().putBoolean(SETUP_DONE,true).putInt("map_setup_schema",MapSetup.SCHEMA).putString(MAP_PROVIDER,which).apply();
            setupDialog.dismiss();chooseProvider(which);
        }));setupDialog.show();
    }
    private void saveKeys() {
        String provider=prefs.getString(MAP_PROVIDER,"amap"),key=text(etKey).trim();
        if(MapProviders.needsKey(provider) && key.isEmpty()) {etKey.setError("请输入 Web 服务 Key");return;}
        MapProviders.saveKey(prefs,provider,key);chooseProvider(provider);toast("Web 服务 Key 已保存在本机");
    }

    private void doSearch() {
        String provider=prefs.getString(MAP_PROVIDER,"amap"),key=MapProviders.key(prefs,provider),address=text(etSearch).trim();
        if (key.isEmpty()) { openSettings(true); toast("请先填写当前服务的 Web 服务 Key"); return; }
        if (address.isEmpty()) { toast("请输入要查找的地点"); return; }
        setBusy(true); io.execute(() -> {
            try {
                double[] point = MapSearchClient.search(provider,key,address);
                ui.post(() -> {
                    routeMap.centerOn(point[0], point[1]);
                    setBusy(false); toast("已找到地点，请将中心标记对准目标");
                });
            } catch (Exception e) { ui.post(() -> { setBusy(false); toast("搜索失败：" + msg(e)); }); }
        });
    }
    private void setBusy(boolean busy) { btnSearch.setEnabled(!busy); btnSaveKey.setEnabled(!busy); }

    private double[] mapCenter() {
        double[] point=routeMap.centerGcj();
        if(point==null || !Double.isFinite(point[0]) || !Double.isFinite(point[1])) {toast("地图尚未就绪，请稍后再试");return null;}
        return point;
    }
    private void readCommonSettings() {
        double value=number(etAccuracy);if(Double.isFinite(value) && value>0) cfg.accuracy=value;
        value=number(etJitter);if(inRange(value,1,3)) cfg.jitterMeters=value;
        cfg.wgs84=swWgs84.isChecked();
    }
    /** Preserve editing and camera state without enabling or changing the system session. */
    private void saveDraft() {
        if(initializing || cfg==null || etAccuracy==null) return;
        readCommonSettings();persistDraft();
    }
    private void persistDraft() {
        SharedPreferences.Editor edit=prefs.edit().putString(EDITOR_DRAFT,cfg.toJson());
        double[] center=routeMap.centerGcj();
        if(center!=null) edit.putString("editor_camera",center[0]+","+center[1]+","+routeMap.cameraZoom());
        if(!edit.commit()) toast("编辑内容保存失败，请检查存储空间");
    }
    private void saveRoute() {
        if(currentPoints().length<2) {toast("请先添加至少两个选点");return;}
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("路线名称");
        name.setText(cfg.routeName.isEmpty() || "地图绘制".equals(cfg.routeName) ? "路线 "+(routeStore.all().size()+1) : cfg.routeName);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("保存路线").setView(name)
                .setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title=text(name).trim();if(title.isEmpty()) {name.setError("请输入路线名称");return;}
            readCommonSettings();
            if(!routeStore.save(title,cfg)) {toast("路线保存失败");return;}
            cfg.routeName=title;persistDraft();dialog.dismiss();toast("路线已保存");
        }));dialog.show();
    }
    private void loadRoute() {
        java.util.List<RouteStore.Entry> entries=routeStore.all();
        if(entries.isEmpty()) {toast("还没有保存的路线");return;}
        String[] names=new String[entries.size()];
        for(int i=0;i<entries.size();i++) names[i]=entries.get(i).name()+" · "+entries.get(i).config().points.length+" 个选点";
        new AlertDialog.Builder(this).setTitle("已保存的路线").setItems(names,(d,index) -> {
            RouteStore.Entry entry=entries.get(index);
            new AlertDialog.Builder(this).setTitle(entry.name()).setMessage(entry.config().points.length+" 个选点")
                    .setPositiveButton("载入",(which,id) -> {
                        Config saved=Config.fromJson(entry.config().toJson());
                        // Preserve the independent single-point editor and global map settings.
                        cfg.points=saved.points;cfg.routeName=entry.name();cfg.speedKmh=saved.speedKmh;
                        cfg.speedRandomPercent=saved.speedRandomPercent;cfg.cadenceMode=saved.cadenceMode;
                        cfg.cadenceSpm=saved.cadenceSpm;cfg.loop=saved.loop;cfg.travelProfile=saved.travelProfile;
                        cfg.mode=Config.MODE_ROUTE;rgMode.check(R.id.rbRoute);syncPoints();
                        double[] point=cfg.points[0];routeMap.centerOn(point[0],point[1]);
                        persistDraft();toast("路线已载入，点击开始后确认参数");
                    }).setNeutralButton("删除",(which,id) -> {
                        if(routeStore.delete(entry.id())) toast("路线已删除");else toast("删除失败");
                    }).setNegativeButton("取消",null).show();
        }).setNegativeButton("取消",null).show();
    }
    private void showRouteOptions() {
        if(currentPoints().length<2) {toast("请先使用添加选点按钮添加至少两个节点");return;}
        if(routeDialog!=null && routeDialog.isShowing()) return;
        View form=getLayoutInflater().inflate(R.layout.dialog_route_options,null);
        Spinner profile=form.findViewById(R.id.spProfile);
        ArrayAdapter<CharSequence> adapter=ArrayAdapter.createFromResource(this,R.array.profiles,android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);profile.setAdapter(adapter);
        EditText speed=form.findViewById(R.id.etSpeed),random=form.findViewById(R.id.etSpeedRandom),cadence=form.findViewById(R.id.etCadence);
        RadioGroup steps=form.findViewById(R.id.rgCadence);Switch loop=form.findViewById(R.id.swLoop);
        speed.setText(num(cfg.speedKmh/3.6));random.setText(num(cfg.speedRandomPercent));cadence.setText(num(cfg.cadenceSpm));
        TextView pace=form.findViewById(R.id.tvPace);
        Runnable updatePace=() -> pace.setText(Pace.summary(number(speed),number(random)));
        TextWatcher paceWatcher=new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) {}
            @Override public void afterTextChanged(Editable text) {updatePace.run();}
        };
        speed.addTextChangedListener(paceWatcher);random.addTextChangedListener(paceWatcher);updatePace.run();
        steps.check(cfg.cadenceMode==1 ? R.id.rbCadenceAuto : cfg.cadenceMode==2 ? R.id.rbCadenceCustom : R.id.rbCadenceOff);
        cadence.setEnabled(cfg.cadenceMode==2);loop.setChecked(cfg.loop);
        steps.setOnCheckedChangeListener((group,id) -> cadence.setEnabled(id==R.id.rbCadenceCustom));
        int initial=TravelProfiles.index(cfg.travelProfile);profile.setSelection(initial);
        final int[] previous={initial};
        profile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) {}
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                if(previous[0]==position) return;previous[0]=position;
                if(position<TravelProfiles.SPEEDS.length-1) {
                    speed.setText(num(TravelProfiles.SPEEDS[position]));
                    steps.check(TravelProfiles.usesSteps(position) ? R.id.rbCadenceAuto : R.id.rbCadenceOff);
                }
            }
        });
        routeDialog=new AlertDialog.Builder(this).setTitle("路线模拟设置").setView(form)
                .setNegativeButton("取消",null).setPositiveButton("开始模拟",null).create();
        routeDialog.setOnShowListener(d -> routeDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double s=number(speed),r=number(random),c=number(cadence);
            if(!inRange(s,0,138.89)) {speed.setError("速度请输入 0–138.89 m/s");return;}
            if(!inRange(r,0,50)) {random.setError("随机幅度请输入 0–50%");return;}
            if(steps.getCheckedRadioButtonId()==R.id.rbCadenceCustom && !inRange(c,0,240)) {cadence.setError("步频请输入 0–240 步/分钟");return;}
            Config next=Config.fromJson(cfg.toJson());next.speedKmh=Math.min(500,s*3.6);next.speedRandomPercent=r;
            next.cadenceMode=steps.getCheckedRadioButtonId()==R.id.rbCadenceAuto ? 1 : steps.getCheckedRadioButtonId()==R.id.rbCadenceCustom ? 2 : 0;
            if(inRange(c,0,240)) next.cadenceSpm=c;
            next.loop=loop.isChecked();next.travelProfile=TravelProfiles.IDS[profile.getSelectedItemPosition()];
            if(!canStart(next.cadenceMode!=0) || !commonSettingsValid()) return;
            cfg=next;readCommonSettings();persistDraft();
            routeDialog.dismiss();beginSimulation(Config.fromJson(cfg.toJson()));
        }));routeDialog.show();
    }

    private static boolean inRange(double value, double min, double max) { return Double.isFinite(value) && value >= min && value <= max; }

    /** A fresh check on each foreground opening, including returning from the background. */
    private void checkEnvironment() {
        if (isDestroyed()) return;
        if (environmentChecking) return;
        if (hotReloading) {recheckPending=true;return;}
        recheckPending=false;
        environmentChecking=true; systemReady=false;
        tvEnvironment.setText("正在检查 ROOT 授权与 LSPosed…"); tvEnvironment.setTextColor(0xFF687F76);
        maintenance.execute(() -> {
            EnvironmentStatus result=inspectEnvironment();
            ui.post(() -> {
                if (isDestroyed()) return;
                environmentChecking=false;
                showEnvironment(result);
                android.util.Log.i("WanderTrace","environment root="+result.root+" live="+result.moduleLive+" compatible="+result.backendCompatible+" location="+result.locationHook+" sensors="+result.sensorHook+" pid="+result.systemPid);
                if (recheckPending) {checkEnvironment();return;}
                if (!automaticReloadAttempted && result.root==EnvironmentStatus.Root.GRANTED && !result.recoveryDisabled
                        && (!result.moduleLive || !result.currentModule || !result.backendCompatible)) {
                    automaticReloadAttempted=true;hotReload(true);
                }
            });
        });
    }
    protected EnvironmentStatus inspectEnvironment() { return RootController.inspect(); }
    protected HotReloadController.Result requestHotReload() {return HotReloadController.request();}
    protected EnvironmentStatus inspectAfterHotReload(String generation) {return RootController.inspectAfterReload(generation);}
    private void hotReload(boolean silent) {
        if (environmentChecking || hotReloading) {if (!silent) toast("请等待当前检查完成");return;}
        if (environment==null || environment.root!=EnvironmentStatus.Root.GRANTED) {if (!silent) toast("请先通过 ROOT 检查");return;}
        EnvironmentStatus before=environment;hotReloading=true;
        reloadNote="后台正在请求框架进程内更新，现有可用后端保持运行。";showEnvironment(before);
        maintenance.execute(() -> {
            HotReloadController.Result result=requestHotReload();
            EnvironmentStatus after=result.accepted() ? inspectAfterHotReload(before.generationId) : inspectEnvironment();
            boolean confirmed=result.accepted() && HotReloadController.confirmed(before,after);
            ui.post(() -> {
                if (isDestroyed()) return;
                hotReloading=false;
                String outcome=confirmed ? "进程内热重载已确认：系统进程 PID 与启动身份保持不变，新版模块心跳已出现。"
                        : result.accepted() ? "框架返回成功，但尚未确认同一进程中的新版心跳，不能判定无中断热重载成功。" : result.message();
                reloadNote=outcome;showEnvironment(after);
                android.util.Log.i("WanderTrace","automaticReload="+silent+" confirmed="+confirmed+" result="+outcome);
                if (!silent) toast(confirmed ? "已在原进程中热重载" : outcome);
                if (recheckPending) checkEnvironment();
            });
        });
    }
    private void showEnvironment(EnvironmentStatus result) {
        environment=result; systemReady=result.ready(active.cadenceMode != 0);
        tvEnvironment.setText(result.summary);
        tvEnvironment.setTextColor(result.ready(false) ? 0xFF087F6D : result.root == EnvironmentStatus.Root.GRANTED ? 0xFFA56711 : 0xFFB23D3D);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (active.enabled && systemReady) {
                RouteEngine.Snap snap = RouteEngine.compute(active, System.currentTimeMillis());
                tvStatus.setText("● 模拟中"); tvStatus.setTextColor(0xFF087F6D);
                tvPreview.setText(String.format(Locale.US, "系统模拟进行中   ·   %.2f m/s\n输出位置 %.6f, %.6f   ·   精度 %.1f m", snap.speedMps, snap.lat, snap.lon, snap.accuracy));
            } else {
                tvStatus.setText(active.enabled ? "等待系统模块" : "未开启"); tvStatus.setTextColor(0xFF687F76);
                tvPreview.setText(active.enabled ? "配置已启用，正在自动检查系统模块。" : "开始后，这里会显示当前模拟状态。");
            }
            ui.postDelayed(this, 1000);
        }
    };
    private void showEnvironmentDetail() {
        String detail=environmentChecking ? "正在自动检查 ROOT 与 LSPosed，请稍候。" : environment==null ? "正在获取运行状态。" : environment.detail;
        new AlertDialog.Builder(this).setTitle("自动检查结果").setMessage(detail+(reloadNote.isEmpty() ? "" : "\n\n"+reloadNote)).setPositiveButton("知道了",null).show();
    }
    private boolean canStart(boolean sensors) {
        if(environmentChecking && environment==null) {toast("正在自动检查，请稍候");return false;}
        if(environment==null || !environment.ready(sensors)) {showEnvironmentDetail();return false;}
        return true;
    }
    private boolean commonSettingsValid() {
        if(!Double.isFinite(number(etAccuracy)) || number(etAccuracy)<=0) {toast("定位精度必须大于 0");return false;}
        if(!inRange(number(etJitter),1,3)) {toast("GPS 抖动半径请输入 1–3 米");return false;}
        return true;
    }
    private void startSingle() {
        if(!canStart(false) || !commonSettingsValid()) return;
        double[] center=mapCenter();if(center==null) return;
        cfg.lat=center[0];cfg.lon=center[1];cfg.singlePointSelected=true;
        readCommonSettings();persistDraft();syncPoints();
        Config next=Config.fromJson(cfg.toJson());next.cadenceMode=0;beginSimulation(next);
    }
    private void beginSimulation(Config next) {
        next.enabled=true;next.startTime=System.currentTimeMillis();writeSystem(next);
    }
    private void stopSpoof() {Config stopped=Config.fromJson(active.toJson());stopped.enabled=false;writeSystem(stopped);}
    private void updateActions() {
        btnStart.setEnabled(!applying);
        btnStart.setText(applying ? "正在应用…" : active.enabled ? "停止模拟" : "开始模拟");
        btnStart.setBackgroundResource(active.enabled ? R.drawable.bg_stop : R.drawable.bg_primary);
        btnAddPoint.setVisibility(cfg.mode==Config.MODE_ROUTE && !active.enabled ? View.VISIBLE : View.GONE);
        btnAddPoint.setEnabled(!applying);
    }
    protected void applySimulation(Config config,boolean stopping) throws Exception {RootController.apply(this,config,stopping);}
    private void writeSystem(Config applied) {
        if(applying) return;
        boolean stopping=active.enabled && !applied.enabled;applying=true;updateActions();
        io.execute(() -> {
            try {
                applySimulation(applied,stopping);applied.save(this);
                EnvironmentStatus checked=inspectEnvironment();
                ui.post(() -> {
                    if(isDestroyed()) return;
                    active=Config.fromJson(applied.toJson());cfg.enabled=active.enabled;showEnvironment(checked);
                    applying=false;updateActions();
                    toast(applied.enabled ? (systemReady ? "系统模拟已应用" : "配置已保存，系统状态待确认") : "系统模拟已停止");
                });
            } catch(Exception failure) {
                ui.post(() -> {
                    if(isDestroyed()) return;
                    applying=false;updateActions();
                    tvEnvironment.setText("系统配置未应用："+msg(failure));toast("系统配置未应用，请检查 ROOT 授权");
                });
            }
        });
    }
    private static String text(EditText e) { return e.getText() == null ? "" : e.getText().toString(); }
    private static double number(EditText e) { try { return Double.parseDouble(text(e).trim()); } catch (Exception ignored) { return Double.NaN; } }
    private static String num(double v) { return Math.abs(v-Math.rint(v)) < 1e-9 ? String.valueOf((long)Math.rint(v)) : String.format(Locale.US, "%.2f", v); }
    private static String msg(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
