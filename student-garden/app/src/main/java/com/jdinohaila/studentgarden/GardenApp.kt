package com.jdinohaila.studentgarden

import android.app.Application
import com.jdinohaila.studentgarden.data.GardenRepository
import com.jdinohaila.studentgarden.platform.PrivateMedia

class GardenApp : Application() {
    var videoCatalog: com.jdinohaila.studentgarden.video.VideoCatalog = com.jdinohaila.studentgarden.video.YoutubeVideoCatalog()
    var frameCapture: com.jdinohaila.studentgarden.video.VideoFrameCapture = com.jdinohaila.studentgarden.video.AndroidVideoFrameCapture()
    val repository by lazy { GardenRepository(this) }
    val media by lazy { PrivateMedia(this) }
}
