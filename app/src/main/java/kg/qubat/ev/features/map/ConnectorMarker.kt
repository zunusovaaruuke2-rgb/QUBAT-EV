package kg.qubat.ev

import android.graphics.*

internal fun connectorMarker(station:Station, selected:Boolean):Bitmap {
    val size=if(selected) 168 else 146
    val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
    val canvas=Canvas(bitmap)
    canvas.scale(size/112f,size/112f)
    val p=Paint(Paint.ANTI_ALIAS_FLAG)
    if(selected) {
        p.color=0x3300C66B
        canvas.drawCircle(56f,53f,55f,p)
    }
    p.color=0x33000000
    canvas.drawCircle(56f,57f,49f,p)
    p.color=0xFFD5DFDC.toInt()
    canvas.drawCircle(56f,55f,48f,p)
    p.color=Color.WHITE
    canvas.drawCircle(56f,51f,48f,p)
    val count=station.connectors.size
    if(count>0) {
        val arc=360f/count
        val gap=minOf(7f,arc*.16f)
        val outer=if(selected) RectF(15f,10f,97f,92f) else RectF(12f,7f,100f,95f)
        val inner=if(selected) RectF(24f,19f,88f,83f) else RectF(31f,26f,81f,76f)
        station.connectors.forEachIndexed { i,port ->
            val start=-90f+i*arc+gap/2
            val sweep=arc-gap
            val segment=Path().apply {
                arcTo(outer,start,sweep,true)
                arcTo(inner,start+sweep,-sweep,false)
                close()
            }
            val color=when(port.state.name) {
                "FREE"->0xFF00C66B.toInt()
                "CONNECTED"->0xFF168AFF.toInt()
                "CHARGING"->0xFFFF3545.toInt()
                "FINISHED","FINISHING"->0xFFFFB500.toInt()
                else->port.state.argb
            }
            val dark=Color.rgb((Color.red(color)*.75f).toInt(),(Color.green(color)*.75f).toInt(),(Color.blue(color)*.75f).toInt())
            p.shader=RadialGradient(56f,51f,44f,intArrayOf(color,color,dark),floatArrayOf(0f,.78f,1f),Shader.TileMode.CLAMP)
            p.pathEffect=CornerPathEffect(3f)
            canvas.drawPath(segment,p)
            p.shader=null
            p.pathEffect=null
        }
    }
    p.color=0xFF10382C.toInt()
    if(selected) {
        p.style=Paint.Style.STROKE
        p.strokeWidth=7f
        p.strokeCap=Paint.Cap.ROUND
        p.strokeJoin=Paint.Join.ROUND
        canvas.drawPath(Path().apply {moveTo(38f,50f);lineTo(51f,64f);lineTo(76f,36f)},p)
    } else {
        p.textSize=if(count>=10) 30f else 38f
        p.textAlign=Paint.Align.CENTER
        p.typeface=Typeface.DEFAULT_BOLD
        canvas.drawText(count.toString(),56f,51f-(p.ascent()+p.descent())/2,p)
    }
    return bitmap
}
