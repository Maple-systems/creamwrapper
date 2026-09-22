package com.mal.creamwrapper

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.mal.creamwrapper.databinding.ActivityMainBinding
import kotlin.jvm.java
import kotlin.math.sin

class MainActivity : AppCompatActivity() {
    private lateinit var v: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        v = ActivityMainBinding.inflate(layoutInflater)
        setContentView(v.root)
        v.vp2.adapter = PageAdapter()
        v.vp2.orientation = ViewPager2.ORIENTATION_VERTICAL//1纵向
        v.vp2.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    //不创类直接用匿名对象
                    if (position == 1) {
                        /*
                    pageOne = recyclerView
       .findViewHolderForAdapterPosition(0)
       as? PageAdapter.PageViewHolder.One
       ?.binding

   pageTwo = recyclerView
       .findViewHolderForAdapterPosition(1)
       as? PageAdapter.PageViewHolder.Two
       ?.binding
}
                   */

                        v.textView.setText("设定页面")
                        v.imageView.visibility = View.GONE

                        val rec = v.vp2.getChildAt(0) as RecyclerView//ViewPage2里面获取其唯一子控件（RecyView），并作为将返回的控件转换为 RecyclerView 类型，使 Kotlin 能调用 RecyclerView 的方法。

                        val pageTwo = rec.findViewHolderForAdapterPosition(1) as? PageAdapter.PageViewHolder.Two

                        //pageTwo?.binding?.textView?.text = "页面二已选中"
                        val intent = Intent(this@MainActivity, a::class.java)
                        intent.flags =Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(intent)

                    } else {
                        v.textView.setText("向下滑动页面查看更多")
                        v.imageView.visibility = View.VISIBLE
                        Toast.makeText(this@MainActivity, "ces", Toast.LENGTH_SHORT).show()
                    }


                }

                override fun onPageScrolled(
                    position: Int,
                    positionOffset: Float,
                    positionOffsetPixels: Int
                ) {

                    val scale = 1f + 0.2f * sin(positionOffset * Math.PI).toFloat()
                    v.textView.scaleX = scale
                    v.textView.scaleY = scale

                }
            }
        )
        /*class PageCallback : ViewPager2.OnPageChangeCallback() {
override fun onPageSelected(position: Int) {
    Log.d("jiem", "$position")
}
}
     */

    }
}