package app.timecapsule.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.timecapsule.TimeCapsuleApp
import app.timecapsule.social.Friend
import app.timecapsule.social.FriendCode
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

/**
 * Friends (parked only). Capsules left "for friends" reach these people: when one of them
 * drives past the spot, their car plays your voice.
 *  - No name yet: pick the name friends will see.
 *  - Then: my code with its QR, a field to add a friend's code, the friend list.
 */
@Composable
fun FriendsScreen(app: TimeCapsuleApp, back: () -> Unit) {
    val me by app.social.me.collectAsState()
    val friends by app.social.friends.collectAsState()
    val problem by app.social.problem.collectAsState()
    val scope = rememberCoroutineScope()
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var removing by remember { mutableStateOf<Friend?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Tc.Bg).padding(Tc.Gutter), verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
        Header("친구", back)
        Column(
            Modifier.weight(1f).widthIn(max = 1100.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val p = me
            if (p == null) {
                SignUp(message) { name ->
                    focus.clearFocus()
                    scope.launch {
                        app.social.signUp(name).onSuccess {
                            message = null
                            // A QR link opened before signing up: add that friend now.
                            app.pendingFriendCode?.let { code ->
                                app.pendingFriendCode = null
                                app.social.addFriend(code).onSuccess { f -> message = "${f.name}님과 친구가 됐어요" }
                            }
                        }.onFailure { message = it.message ?: "시작하지 못했어요" }
                    }
                }
                return@Column
            }

            if (problem != null) ListRow(problem!!, "연결되면 저절로 이어져요", leading = Icons.Rounded.CloudOff, leadingTint = Tc.TextMute)

            Section("내 친구 코드")
            MyCode(p.name, p.code)

            Spacer(Modifier.height(8.dp))
            Section("친구 추가")
            AddFriend(message) { code ->
                focus.clearFocus()
                scope.launch {
                    app.social.addFriend(code)
                        .onSuccess { message = "${it.name}님과 친구가 됐어요" }
                        .onFailure { message = it.message ?: "추가하지 못했어요" }
                }
            }

            Spacer(Modifier.height(8.dp))
            Section("친구 ${friends.size}")
            Panel {
                if (friends.isEmpty()) {
                    ListRow("아직 친구가 없어요", "친구 코드를 받아 위에 입력해 보세요", leading = Icons.Rounded.Person, leadingTint = Tc.TextMute)
                }
                friends.forEachIndexed { i, f ->
                    if (i > 0) RowDivider()
                    ListRow(f.name, "코드 ${spaced(f.code)} · 내 캡슐이 이 친구 차에서도 열려요", leading = Icons.Rounded.Person) {
                        IconBtn(Icons.Rounded.PersonRemove, "친구 끊기") { removing = f }
                    }
                }
            }
        }
    }
    removing?.let { f ->
        ConfirmPopup(
            "${f.name}님과 친구를 끊을까요?",
            "서로의 캡슐이 더는 전해지지 않아요. 이미 받은 캡슐도 차에서 사라져요.",
            "끊기",
            onConfirm = { scope.launch { app.social.removeFriend(f) }; removing = null },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun SignUp(message: String?, onStart: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Text("친구에게 보일 이름", style = Type.TitleS, color = Tc.Text, modifier = Modifier.padding(start = Tc.Inset, top = 8.dp))
    Text(
        "친구가 내 캡슐을 지나가면 \"○○님이 남긴 캡슐이 있어요\"라고 알려 줘요",
        style = Type.BodyS, color = Tc.TextMute, modifier = Modifier.padding(start = Tc.Inset, bottom = 8.dp),
    )
    Field(name, { name = it.take(12) }, "이름", KeyboardCapitalization.None) { if (name.isNotBlank()) onStart(name.trim()) }
    if (message != null) Text(message, style = Type.LabelM, color = Tc.TextSub, modifier = Modifier.padding(start = Tc.Inset))
    Btn("시작하기", Modifier.fillMaxWidth(), style = BtnStyle.Filled, enabled = name.isNotBlank(), height = Tc.TouchLarge) {
        onStart(name.trim())
    }
}

/**
 * My code, big enough to read aloud, and the same code as a QR (it opens
 * timecapsule://friend?code=..., which adds me as a friend).
 */
@Composable
private fun MyCode(name: String, code: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth > 700.dp
        val qr = remember(code) { qrMatrix(FriendCode.link(code)) }
        val text: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(spaced(code), style = Type.Display.copy(fontSize = Type.TitleL.fontSize * 1.6f, lineHeight = Type.TitleL.lineHeight * 1.6f), color = Tc.Text)
                Text("${name}님의 코드", style = Type.BodyStrong, color = Tc.Text)
                Text("친구가 이 코드를 입력하거나 QR을 찍으면 서로 친구가 돼요", style = Type.LabelM, color = Tc.TextMute)
            }
        }
        if (wide) {
            Row(Modifier.padding(horizontal = Tc.Inset), verticalAlignment = Alignment.CenterVertically) {
                QrCode(qr, 240.dp)
                Spacer(Modifier.size(32.dp))
                text()
            }
        } else {
            Column(Modifier.padding(horizontal = Tc.Inset), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                text()
                QrCode(qr, 240.dp)
            }
        }
    }
}

@Composable
private fun AddFriend(message: String?, onAdd: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    val ok = FriendCode.normalize(code) != null
    Row(Modifier.fillMaxWidth().padding(horizontal = Tc.Inset), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Field(code, { code = it.uppercase().filter(Char::isLetterOrDigit).take(FriendCode.LENGTH) }, "친구 코드 6자리", KeyboardCapitalization.Characters) {
                if (ok) { onAdd(code); code = "" }
            }
        }
        Btn("추가", style = BtnStyle.Filled, enabled = ok) { onAdd(code); code = "" }
    }
    if (message != null) Text(message, style = Type.LabelM, color = Tc.TextSub, modifier = Modifier.padding(start = Tc.Inset))
}

/** Text field in the Pleos input style: a filled basic box, no underline. */
@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, caps: KeyboardCapitalization, onDone: () -> Unit) {
    val style = TextStyle(fontFamily = Type.BodyStrong.fontFamily, fontSize = Type.BodyStrong.fontSize, fontWeight = Type.BodyStrong.fontWeight, color = Tc.Text)
    Surface(shape = RoundedCornerShape(Tc.BtnRadius), color = Tc.Basic, modifier = Modifier.fillMaxWidth().heightIn(min = Tc.Touch)) {
        Box(Modifier.padding(horizontal = 28.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, style = Type.BodyS, color = Tc.TextMute)
            BasicTextField(
                value, onChange, singleLine = true, textStyle = style, cursorBrush = SolidColor(Tc.Text),
                keyboardOptions = KeyboardOptions(capitalization = caps, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Groups of three, easier to read aloud: "MNS U27". */
internal fun spaced(code: String) = if (code.length == 6) "${code.take(3)} ${code.drop(3)}" else code

private fun qrMatrix(text: String): BitMatrix =
    QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))

/** Dark modules on white with a quiet zone, also at night (scanners need the contrast). */
@Composable
private fun QrCode(m: BitMatrix, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).background(Color.White, RoundedCornerShape(12.dp)).padding(16.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = this.size.width / m.width
            for (y in 0 until m.height) for (x in 0 until m.width) {
                if (m[x, y]) drawRect(Color(0xFF131417), Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}
