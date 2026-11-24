package jp.swingchat

import android.app.Activity
import android.os.Build
import android.os.Bundle
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
        root.addView(label("SWING ↗",36f));root.addView(label("拍に合わせて、空中にことばを。",18f))
        root.addView(label("センサー不要 · 片方向の残像表示\n別色マークで前へ振り、消灯中に戻します。"))
        root.addView(label("メッセージ（まずは1文字がおすすめ）"))
        message=EditText(this).apply { setText(prefs.getString("text","H"));setTextColor(Color.WHITE);setSingleLine();filters=arrayOf(InputFilter.LengthFilter(4));textSize=30f };root.addView(message)
        fun slider(title: String, min: Int, max: Int, value: Int, unit: String): SeekBar {
            val text=label("$title  $value$unit");root.addView(text)
            return SeekBar(this).apply { this.min=min;this.max=max;progress=value;root.addView(this);setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(s:SeekBar?,p:Int,user:Boolean){ text.text="$title  $p$unit";if(::estimate.isInitialized) updateEstimate() }
                override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){}
            }) }
        }
        tempo=slider("テンポ",40,160,prefs.getInt("bpm",80)," BPM")
        sweep=slider("1拍のうち表示する時間",35,80,prefs.getInt("sweep",65),"%")
        brightness=slider("画面の明るさ",20,100,prefs.getInt("brightness",80),"%")
        fun spinner(title:String,items:List<String>,index:Int):Spinner {root.addView(label(title));return Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,items);setSelection(index);root.addView(this) } }
        resolution=spinner("横の解像度",listOf("16列（粗め）","24列（標準）","32列（細かめ）"),prefs.getInt("resolution",0))
        palette=spinner("文字の色",listOf("ライム / 合図はピンク","ホワイト / 合図はピンク","ブルー / 合図はピンク","ピンク / 合図はブルー"),prefs.getInt("color",0))
        reverse=CheckBox(this).apply { text="文字の進む方向を反転";setTextColor(Color.LTGRAY);isChecked=prefs.getBoolean("reverse",false) };root.addView(reverse)
        estimate=label("");root.addView(estimate);updateEstimate()
        root.addView(Button(this).apply { text="4拍カウントして開始 ↗";setOnClickListener { startLight() } })
        root.addView(label("開始後は画面の上下に出る別色マークが拍の合図です。文字列の先頭と同時に点灯します。\n\n設定したテンポに合わせて、ライブで拍を取るように片方向へ。文字が逆に見える場合は反転してください。\n\nストラップを使い、周囲に十分な空間を確保してください。強い点滅が苦手な方は使用を控えてください。\n\n高リフレッシュレートを優先しますが、実際の速度は端末設定・省電力・温度などに依存します。"))
        setContentView(scroll)
    }
    @Suppress("DEPRECATION") private fun currentDisplay() = windowManager.defaultDisplay
    private fun updateEstimate() {
        if(!::tempo.isInitialized||!::sweep.isInitialized)return
        val rate=currentDisplay().refreshRate
        val available=rate*60f/tempo.progress*sweep.progress/100f
        estimate.text=String.format(Locale.JAPAN,"現在の表示モード: %.0f Hz\n1回の表示時間: %.0f ms / 更新機会の目安: %.0f列\n描画中の実測fpsは表示画面の下部に出ます。",rate,60000f/tempo.progress*sweep.progress/100,available)
    }
    private fun startLight() {
        val text=message.text.toString().trim().ifEmpty { "HI" }
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
        light=LightView(this,text,timeline,colors[palette.selectedItemPosition],reverse.isChecked)
        stage.addView(light,FrameLayout.LayoutParams(-1,-1))
        val exit=Button(this).apply { this.text="終了 ×";alpha=.65f;setOnClickListener { stopLight() } }
        stage.addView(exit,FrameLayout.LayoutParams(dp(100),dp(54),Gravity.BOTTOM or Gravity.END).apply {setMargins(dp(16),0,dp(16),dp(28))})
        setContentView(stage)
        if(Build.VERSION.SDK_INT>=30){window.insetsController?.apply { hide(WindowInsets.Type.systemBars());systemBarsBehavior=WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }}
        else @Suppress("DEPRECATION") window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
    private fun stopLight() {
        if(light==null)return
        light?.halt();light=null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val attrs=window.attributes;attrs.screenBrightness=savedBrightness;attrs.preferredDisplayModeId=savedMode;attrs.preferredRefreshRate=savedRate;window.attributes=attrs
        if(Build.VERSION.SDK_INT>=30)window.insetsController?.show(WindowInsets.Type.systemBars())
        else @Suppress("DEPRECATION") window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_VISIBLE
        settings()
    }
    override fun onPause(){stopLight();super.onPause()}
    @Deprecated("Platform back callback for minSdk 26") override fun onBackPressed(){if(light!=null)stopLight() else super.onBackPressed()}
}

private class LightView(context:Context,text:String,private val timeline:BeatTimeline,private val ink:Int,private val reverse:Boolean): View(context),Choreographer.FrameCallback {
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
        contentDescription="拍に同期した片方向の文字表示"
    }
    override fun onAttachedToWindow(){super.onAttachedToWindow();Choreographer.getInstance().postFrameCallback(this)}
    override fun doFrame(frameTimeNanos:Long){
        if(!active)return
        if(start==0L){start=frameTimeNanos;sampleStart=start}
        now=frameTimeNanos;frames++
        if(now-sampleStart>=1_000_000_000L){fps=frames*1_000_000_000f/(now-sampleStart);frames=0;sampleStart=now}
        invalidate();Choreographer.getInstance().postFrameCallback(this)
    }
    fun halt(){active=false;Choreographer.getInstance().removeFrameCallback(this)}
    override fun onDetachedFromWindow(){halt();super.onDetachedFromWindow()}
    override fun onDraw(canvas:Canvas){
        canvas.drawColor(Color.BLACK)
        val state=timeline.at(now-start)
        paint.style=Paint.Style.FILL
        if(state.column>=0){
            val index=if(reverse)timeline.columns-1-state.column else state.column
            val stripe=width*.055f;val height=height*.72f;val top=(this.height-height)/2f
            paint.color=ink
            for(row in 0..15)if(pixels[index][row]) canvas.drawRect((width-stripe)/2f,top+row*height/16,(width+stripe)/2f,top+(row+.9f)*height/16,paint)
        }
        if(state.marker){paint.color=marker;val half=width*.07f;canvas.drawRect(width/2f-half,height*.055f,width/2f+half,height*.09f,paint);canvas.drawRect(width/2f-half,height*.87f,width/2f+half,height*.905f,paint)}
        if(state.countdown>0){paint.color=Color.WHITE;paint.textAlign=Paint.Align.CENTER;paint.textSize=width*.2f;canvas.drawText(state.countdown.toString(),width/2f,height*.47f,paint);paint.textSize=width*.034f;canvas.drawText("別色マークに合わせて、腕を前へ",width/2f,height*.56f,paint)}
        paint.color=Color.DKGRAY;paint.textAlign=Paint.Align.LEFT;paint.textSize=resources.displayMetrics.scaledDensity*10
        val available=fps*timeline.sweepNanos/1_000_000_000f
        canvas.drawText(String.format(Locale.JAPAN,"%.0f fps · %d BPM · %d列 / 更新目安 %.0f列",fps,timeline.bpm,timeline.columns,available),16f,height*.96f,paint)
    }
}
