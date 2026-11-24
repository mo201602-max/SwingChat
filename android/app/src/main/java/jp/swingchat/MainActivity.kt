package jp.swingchat

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.hardware.*
import android.graphics.*
import android.content.Context
import android.view.*
import android.widget.*
import android.text.InputFilter
import java.util.Locale

class MainActivity : Activity() {
    private val lime = Color.rgb(196,245,107)
    private var light: LightView? = null
    private lateinit var message: EditText
    private lateinit var resolution: Spinner
    private lateinit var palette: Spinner
    private lateinit var mode: Spinner
    private lateinit var axis: Spinner
    private lateinit var angleSpan: SeekBar
    private lateinit var tempo: SeekBar
    private lateinit var sweep: SeekBar
    private lateinit var brightness: SeekBar
    private lateinit var reverse: CheckBox
    private lateinit var estimate: TextView
    private var savedBrightness = -1f
    private var savedMode = 0
    private var savedRate = 0f
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun label(text: String, size: Float = 14f) = TextView(this).apply { this.text=text; textSize=size; setTextColor(Color.LTGRAY); setPadding(0,dp(10),0,dp(6)) }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); settings() }
    private fun settings() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(24),dp(18),dp(24),dp(24)); setBackgroundColor(Color.rgb(17,22,19)) }
        val scroll=ScrollView(this).apply { addView(root); isFillViewport=true }
        if(Build.VERSION.SDK_INT>=30) scroll.setOnApplyWindowInsetsListener { v,ins -> val s=ins.getInsets(WindowInsets.Type.systemBars());v.setPadding(s.left,s.top,s.right,s.bottom);ins }
        else scroll.fitsSystemWindows=true
        root.addView(label("SWING ↗",36f));root.addView(label("角度に合わせて、空中にことばを。",18f))
        root.addView(label("角度モードは往復表示。中央の向きを設定し、\n同じ角度に同じ文字の列を描きます。"))
        root.addView(label("メッセージ（まずは1文字がおすすめ）"))
        message=EditText(this).apply { setText(prefs.getString("text","H"));setTextColor(Color.WHITE);setSingleLine();filters=arrayOf(InputFilter.LengthFilter(4));textSize=30f };root.addView(message)
        fun slider(title: String, min: Int, max: Int, value: Int, unit: String): SeekBar {
            val text=label("$title  $value$unit");root.addView(text)
            return SeekBar(this).apply { this.min=min;this.max=max;progress=value;root.addView(this);setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(s:SeekBar?,p:Int,user:Boolean){ text.text="$title  $p$unit";if(::estimate.isInitialized) updateEstimate() }
                override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){}
            }) }
        }
        root.addView(label("表示方式"))
        mode=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("角度に同期（往復）","一定テンポ（片方向・従来方式）"));setSelection(prefs.getInt("mode",0));root.addView(this) }
        angleSpan=slider("表示する角度の幅",30,160,prefs.getInt("angleSpan",120),"°")
        root.addView(label("角度を測る軸"))
        axis=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("左右に振る（スマホの縦軸）","前後に振る（スマホの横軸）"));setSelection(prefs.getInt("axis",0));root.addView(this) }
        tempo=slider("テンポ",40,160,prefs.getInt("bpm",80)," BPM")
        sweep=slider("1拍のうち表示する時間",35,80,prefs.getInt("sweep",65),"%")
        brightness=slider("画面の明るさ",20,100,prefs.getInt("brightness",80),"%")
        fun spinner(title:String,items:List<String>,index:Int):Spinner {root.addView(label(title));return Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,items);setSelection(index);root.addView(this) } }
        resolution=spinner("横の解像度",listOf("16列（粗め）","24列（標準）","32列（細かめ）"),prefs.getInt("resolution",0))
        palette=spinner("文字の色",listOf("ライム / 合図はピンク","ホワイト / 合図はピンク","ブルー / 合図はピンク","ピンク / 合図はブルー"),prefs.getInt("color",0))
        reverse=CheckBox(this).apply { text="文字の進む方向を反転";setTextColor(Color.LTGRAY);isChecked=prefs.getBoolean("reverse",false) };root.addView(reverse)
        estimate=label("");root.addView(estimate);updateEstimate()
        val startButton=Button(this).apply { setOnClickListener { startLight() } };root.addView(startButton)
        fun refreshMode(){ val angleMode=mode.selectedItemPosition==0;angleSpan.isEnabled=angleMode;axis.isEnabled=angleMode;tempo.isEnabled=!angleMode;sweep.isEnabled=!angleMode;startButton.text=if(angleMode) "中央を向けて開始 ↗" else "4拍カウントして開始 ↗";updateEstimate() }
        mode.onItemSelectedListener=object:AdapterView.OnItemSelectedListener { override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long){refreshMode()};override fun onNothingSelected(p:AdapterView<*>?){} };refreshMode()
        root.addView(label("角度モード：スマホを縦に持ち、振る範囲の中央を向けて開始します。初期値は左右60°ずつ、合計120°。往路と復路の同じ角度に同じ列を表示します。表示範囲の外や静止中は消灯します。\n\n中央がずれたら画面の「ここを中央に」で設定し直せます。文字列の先頭の角度では別色マークが点灯します。左右が逆なら反転を使ってください。\n\n肘や肩を中心に、スマホが弧を描いて移動し、向きも変わるように振ってください。その場で回すだけ・向きを固定して平行移動するだけではうまく描けません。\n\nストラップを使い、周囲に十分な空間を確保してください。強い点滅が苦手な方は使用を控えてください。\n\n高リフレッシュレートを優先しますが、実際の速度は端末設定・省電力・温度などに依存します。"))
        setContentView(scroll)
    }
    @Suppress("DEPRECATION") private fun currentDisplay() = windowManager.defaultDisplay
    private fun updateEstimate() {
        if(!::tempo.isInitialized||!::sweep.isInitialized)return
        val rate=currentDisplay().refreshRate
        if(::mode.isInitialized && mode.selectedItemPosition==0) {
            val manager=getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor=manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            estimate.text=if(sensor==null) "回転センサーが使えません。一定テンポ方式を選んでください。" else "${angleSpan.progress}°を往復表示 · 現在の表示モード: ${rate.toInt()} Hz\n開始時に中央を設定します。センサーの種類と相対角度は表示中に確認できます。"
            return
        }
        val available=rate*60f/tempo.progress*sweep.progress/100f
        estimate.text=String.format(Locale.JAPAN,"現在の表示モード: %.0f Hz\n1回の表示時間: %.0f ms / 更新機会の目安: %.0f列\n描画中の実測fpsは表示画面の下部に出ます。",rate,60000f/tempo.progress*sweep.progress/100,available)
    }
    private fun startLight() {
        val angleMode=mode.selectedItemPosition==0
        if(angleMode){val manager=getSystemService(Context.SENSOR_SERVICE) as SensorManager;if(manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)==null && manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)==null){Toast.makeText(this,"回転センサーが使えません。一定テンポ方式を選んでください。",Toast.LENGTH_LONG).show();return}}
        val text=message.text.toString().trim().ifEmpty { "H" }
        prefs.edit().putInt("mode",mode.selectedItemPosition).putInt("angleSpan",angleSpan.progress).putInt("axis",axis.selectedItemPosition).apply()
        prefs.edit().putString("text",text).putInt("bpm",tempo.progress).putInt("sweep",sweep.progress).putInt("brightness",brightness.progress).putInt("resolution",resolution.selectedItemPosition).putInt("color",palette.selectedItemPosition).putBoolean("reverse",reverse.isChecked).apply()
        val colors=intArrayOf(lime,Color.WHITE,Color.rgb(114,202,255),Color.rgb(255,140,186))
        val colCount=intArrayOf(16,24,32)[resolution.selectedItemPosition]
        val timeline=BeatTimeline(tempo.progress,sweep.progress,colCount)
        val attrs=window.attributes;savedBrightness=attrs.screenBrightness;savedMode=attrs.preferredDisplayModeId;savedRate=attrs.preferredRefreshRate
        val display=currentDisplay();val current=display.mode
        val best=display.supportedModes.filter { it.physicalWidth==current.physicalWidth&&it.physicalHeight==current.physicalHeight }.maxByOrNull { it.refreshRate }
        attrs.preferredDisplayModeId=best?.modeId?:current.modeId;attrs.preferredRefreshRate=best?.refreshRate?:display.refreshRate;attrs.screenBrightness=brightness.progress/100f;window.attributes=attrs
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val stage=FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        light=LightView(this,text,timeline,colors[palette.selectedItemPosition],reverse.isChecked,if(angleMode)angleSpan.progress else null,if(axis.selectedItemPosition==0)2 else 1)
        stage.addView(light,FrameLayout.LayoutParams(-1,-1))
        val exit=Button(this).apply { this.text="終了 ×";alpha=.65f;setOnClickListener { stopLight() } }
        stage.addView(exit,FrameLayout.LayoutParams(dp(100),dp(54),Gravity.BOTTOM or Gravity.END).apply {setMargins(dp(16),0,dp(16),dp(28))})
        if(angleMode){val center=Button(this).apply { this.text="ここを中央に";alpha=.65f;setOnClickListener { light?.recenter() } };stage.addView(center,FrameLayout.LayoutParams(dp(150),dp(54),Gravity.BOTTOM or Gravity.START).apply{setMargins(dp(16),0,0,dp(28))})}
        setContentView(stage)
        if(Build.VERSION.SDK_INT>=30){window.insetsController?.apply { hide(WindowInsets.Type.systemBars());systemBarsBehavior=WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }}
        else window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
    private fun stopLight() {
        if(light==null)return
        light?.halt();light=null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val attrs=window.attributes;attrs.screenBrightness=savedBrightness;attrs.preferredDisplayModeId=savedMode;attrs.preferredRefreshRate=savedRate;window.attributes=attrs
        if(Build.VERSION.SDK_INT>=30)window.insetsController?.show(WindowInsets.Type.systemBars())
        else window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_VISIBLE
        settings()
    }
    override fun onPause(){stopLight();super.onPause()}
    @Deprecated("Platform back callback for minSdk 26") override fun onBackPressed(){if(light!=null)stopLight() else super.onBackPressed()}
}

private class LightView(context:Context,text:String,private val timeline:BeatTimeline,private val ink:Int,private val reverse:Boolean,span:Int?,axis:Int): View(context),Choreographer.FrameCallback,SensorEventListener {
    private val angleSweep=span?.let{AngleSweep(it,timeline.columns,reverse)}
    private val tracker=RelativeAngleTracker(axis)
    private val sensorManager=context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor=if(span==null)null else sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val quaternion=FloatArray(4)
    private var sensorReady=false
    private var sensorError=false
    private var centeredAt=0L
    private var lastSensor=0L
    private var lastAccuracy=SensorManager.SENSOR_STATUS_ACCURACY_HIGH
    fun recenter(){tracker.recenter();angleSweep?.recenter();sensorReady=false;lastSensor=0;centeredAt=0}
    override fun onSensorChanged(event:SensorEvent){
        if(!active || angleSweep==null)return
        SensorManager.getQuaternionFromVector(quaternion,event.values)
        val degrees=tracker.sample(quaternion[0].toDouble(),quaternion[1].toDouble(),quaternion[2].toDouble(),quaternion[3].toDouble())
        if(degrees==null){angleSweep.recenter();sensorReady=false;return}
        if(!sensorReady)centeredAt=event.timestamp
        sensorReady=true;lastSensor=event.timestamp;angleSweep.sample(degrees,event.timestamp)
    }
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){lastAccuracy=accuracy}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val pixels: Array<BooleanArray>
    private var active=true
    private var start=0L
    private var now=0L
    private var frames=0
    private var sampleStart=0L
    private var fps=0f
    private val marker=if(ink==Color.rgb(255,140,186))Color.rgb(114,202,255) else Color.rgb(255,105,168)
    init {
        // Rasterize once. Native Noto fallback allows Japanese glyphs too.
        val bitmap=Bitmap.createBitmap(timeline.columns,16,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        paint.color=Color.WHITE;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);paint.textSize=16f;paint.textAlign=Paint.Align.CENTER
        val bounds=Rect();paint.getTextBounds(text,0,text.length,bounds)
        paint.textScaleX=minOf(1f,(timeline.columns-2f)/paint.measureText(text))
        val baseline=8f-(paint.fontMetrics.ascent+paint.fontMetrics.descent)/2f
        canvas.drawText(text,timeline.columns/2f,baseline,paint)
        pixels=Array(timeline.columns){x->BooleanArray(16){y->Color.alpha(bitmap.getPixel(x,y))>=100} };bitmap.recycle()
        paint.textScaleX=1f
        contentDescription=if(angleSweep==null)"拍に同期した片方向の文字表示" else "角度に同期した往復の文字表示"
    }
    override fun onAttachedToWindow(){super.onAttachedToWindow();if(angleSweep!=null){recenter();sensorError=sensor==null || !sensorManager.registerListener(this,sensor,10_000)};Choreographer.getInstance().postFrameCallback(this)}
    override fun doFrame(frameTimeNanos:Long){
        if(!active)return
        if(start==0L){start=frameTimeNanos;sampleStart=start}
        now=frameTimeNanos;frames++
        if(now-sampleStart>=1_000_000_000L){fps=frames*1_000_000_000f/(now-sampleStart);frames=0;sampleStart=now}
        invalidate();Choreographer.getInstance().postFrameCallback(this)
    }
    fun halt(){active=false;sensorManager.unregisterListener(this);Choreographer.getInstance().removeFrameCallback(this)}
    override fun onDetachedFromWindow(){halt();super.onDetachedFromWindow()}
    override fun onDraw(canvas:Canvas){
        canvas.drawColor(Color.BLACK)
        val state=timeline.at(now-start)
        val sensorNow=SystemClock.elapsedRealtimeNanos()
        val angleColumn=angleSweep?.column(sensorNow) ?: -1
        val column=if(angleSweep==null)state.column else angleColumn
        val showMarker=if(angleSweep==null)state.marker else angleColumn==0
        paint.style=Paint.Style.FILL
        if(column>=0){
            val index=if(angleSweep!=null)column else if(reverse)timeline.columns-1-column else column
            val stripe=width*.055f;val height=height*.72f;val top=(this.height-height)/2f
            paint.color=ink
            for(row in 0..15)if(pixels[index][row]) canvas.drawRect((width-stripe)/2f,top+row*height/16,(width+stripe)/2f,top+(row+.9f)*height/16,paint)
        }
        if(showMarker){paint.color=marker;val half=width*.07f;canvas.drawRect(width/2f-half,height*.055f,width/2f+half,height*.09f,paint);canvas.drawRect(width/2f-half,height*.87f,width/2f+half,height*.905f,paint)}
        if(angleSweep==null && state.countdown>0){paint.color=Color.WHITE;paint.textAlign=Paint.Align.CENTER;paint.textSize=width*.2f;canvas.drawText(state.countdown.toString(),width/2f,height*.47f,paint);paint.textSize=width*.034f;canvas.drawText("別色マークに合わせて、腕を前へ",width/2f,height*.56f,paint)}
        paint.color=Color.DKGRAY;paint.textAlign=Paint.Align.LEFT;paint.textSize=resources.displayMetrics.scaledDensity*10
        if(angleSweep!=null){
            val stale=sensorReady && sensorNow-lastSensor>150_000_000L
            val hint=when { sensorError -> "回転センサーを開始できませんでした";!sensorReady -> "中央を設定中…";stale -> "センサーの更新が止まっています";sensorNow-centeredAt<1_000_000_000L -> "中央を設定しました。弧を描いて振ってください";else -> null }
            if(hint!=null){paint.color=Color.LTGRAY;paint.textAlign=Paint.Align.CENTER;paint.textSize=width*.033f;canvas.drawText(hint,width/2f,height*.5f,paint)}
            paint.color=Color.DKGRAY;paint.textAlign=Paint.Align.LEFT;paint.textSize=resources.displayMetrics.scaledDensity*10
            canvas.drawText(String.format(Locale.JAPAN,"%.0f fps · %+.1f° / ±%.0f° · %s%s",fps,angleSweep.angle,angleSweep.spanDegrees/2f,if(sensor?.type==Sensor.TYPE_GAME_ROTATION_VECTOR)"GAME ROTATION" else "ROTATION",if(lastAccuracy==SensorManager.SENSOR_STATUS_UNRELIABLE)" · 精度低下" else ""),16f,height*.035f,paint)
            return
        }
        val available=fps*timeline.sweepNanos/1_000_000_000f
        canvas.drawText(String.format(Locale.JAPAN,"%.0f fps · %d BPM · %d列 / 更新目安 %.0f列",fps,timeline.bpm,timeline.columns,available),16f,height*.96f,paint)
    }
}
