require "import"
import "com.androlua.LuaDialog"
import "android.widget.LinearLayout"
import "android.widget.ScrollView"
import "android.widget.TextView"
import "android.widget.Button"
import "android.widget.ListView"
import "android.widget.ArrayAdapter"
import "android.widget.AdapterView"
import "android.widget.Toast"
import "java.util.ArrayList"
import "android.os.Handler"
import "android.os.Looper"
import "android.media.MediaPlayer"
import "android.view.View"
import "java.io.File"
import "java.lang.Thread"
import "java.lang.Runnable"

local ctx = activity or service or this
local ui = Handler(Looper.getMainLooper())
local File = luajava.bindClass("java.io.File")
local NAMA = "Vocal Remover CSR - Development"
local EKSTENSI = {
  mp3 = true, m4a = true, aac = true, wav = true,
  flac = true, ogg = true, opus = true
}

local DJL = _G.VocalRemover or {}
_G.VocalRemover = DJL
local dialogAktif
local pemutar
local pantauId
local unduhPantau = false
local hasilTerakhir
local Bridge
local menuUtama, pilihAudio, mulaiPemisahan, pantau
local mulaiUnduhModel, statusDialogModel, tampilHasil, putar, salinLokasi

local function aman(fn)
  return function(...)
    local jumlah = select("#", ...)
    local argumen = { ... }
    local unp = table.unpack or unpack
    local ok, a, b = xpcall(function()
      return fn(unp(argumen, 1, jumlah))
    end, function(e)
      local t = tostring(e)
      pcall(function()
        if debug and debug.traceback then t = t .. "\n" .. debug.traceback("", 2) end
      end)
      return t
    end)
    if not ok then
      pcall(function() Toast.makeText(ctx, "Galat: " .. tostring(a), 1).show() end)
      return nil
    end
    return a, b
  end
end

local function tunda(ms, fn)
  ui.postDelayed(Runnable({ run = aman(fn) }), ms)
end

local function toast(teks)
  ui.post(Runnable({ run = function()
    pcall(function() Toast.makeText(ctx, tostring(teks), 1).show() end)
  end }))
end

local function panjangLarik(a)
  local n = 0
  local ok = pcall(function() n = a.length end)
  if not ok or type(n) ~= "number" then pcall(function() n = #a end) end
  return n
end

local function dp(n)
  local d = 1
  pcall(function() d = ctx.getResources().getDisplayMetrics().density end)
  return math.floor(n * d + 0.5)
end

local function T(teks, ukuran)
  local v = TextView(ctx)
  v.setText(tostring(teks or ""))
  v.setTextSize(ukuran or 16)
  v.setPadding(dp(8), dp(6), dp(8), dp(6))
  return v
end

local function B(teks, fn)
  local b = Button(ctx)
  b.setText(teks)
  b.setAllCaps(false)
  b.setOnClickListener(View.OnClickListener({
    onClick = function() aman(fn)() end
  }))
  return b
end

local function dialogBaru(judul)
  local d = LuaDialog(ctx)
  if judul then d.setTitle(judul) end
  return d
end

local function bukaDialog(d)
  local lama = dialogAktif
  dialogAktif = d
  if lama and lama ~= d then pcall(function() lama.dismiss() end) end
  d.show()
end

local function tutupDialog()
  local d = dialogAktif
  dialogAktif = nil
  if d then pcall(function() d.dismiss() end) end
end

local function susunKolom(...)
  local l = LinearLayout(ctx)
  l.setOrientation(1)
  l.setPadding(dp(12), dp(8), dp(12), dp(8))
  for i = 1, select("#", ...) do
    local v = select(i, ...)
    if v then l.addView(v) end
  end
  local s = ScrollView(ctx)
  s.addView(l)
  return s
end

local function dialogPesan(judul, pesan, tombol, kembali)
  local d = dialogBaru(judul)
  d.setMessage(tostring(pesan or ""))
  local a, b, c = tombol[1], tombol[2], tombol[3]
  if a then d.setButton(a[1], aman(a[2])) end
  if b then d.setButton2(b[1], aman(b[2])) end
  if c then d.setButton3(c[1], aman(c[2])) end
  if kembali then d.setOnCancelListener(aman(kembali)) end
  bukaDialog(d)
  return d
end

local function layarDaftar(judul, butir, pilih, kembali, atas)
  local d = dialogBaru(judul)
  local l = LinearLayout(ctx)
  l.setOrientation(1)
  l.setPadding(dp(8), dp(4), dp(8), dp(4))
  if atas then l.addView(atas) end
  local arr = ArrayList()
  for _, item in ipairs(butir) do arr.add(tostring(item)) end
  local lv = ListView(ctx)
  lv.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_list_item_1, arr))
  lv.setOnItemClickListener(AdapterView.OnItemClickListener({
    onItemClick = function(parent, view, posisi, id)
      aman(function() pilih(posisi + 1) end)()
    end
  }))
  l.addView(lv, LinearLayout.LayoutParams(-1, -2, 1.0))
  d.setView(l)
  d.setButton("Kembali", aman(kembali or function() menuUtama() end))
  d.setOnCancelListener(aman(kembali or function() menuUtama() end))
  bukaDialog(d)
  return d
end

local function cariBase()
  local base = nil
  pcall(function()
    local path = package.searchpath("main", package.path)
    if path then base = string.match(path, "^(.*/)") end
  end)
  return base
end

local function siapkanBridge()
  if Bridge then return true end
  local base = cariBase()
  local path = base and (base .. "bridge/lua/DJLBridge.lua") or nil
  if not path or not File(path).isFile() then
    return false, "Berkas bridge Lua tidak ditemukan di folder ekstensi"
  end
  local ok, modul = pcall(function() return assert(loadfile(path))() end)
  if not ok or type(modul) ~= "table" then
    return false, "Gagal membuka bridge Lua: " .. tostring(modul)
  end
  Bridge = modul
  local siap, pesan = pcall(function() return Bridge.mulai(ctx, base) end)
  if not siap then
    Bridge = nil
    return false, tostring(pesan)
  end
  return true
end

tampilHasil = function(id, pathLagu)
  local daftar, galat = Bridge.ambilHasil(id)
  if not daftar then
    dialogPesan("Hasil belum bisa dibaca", galat, {
      { "Tutup", function() menuUtama() end }
    }, function() menuUtama() end)
    return
  end
  hasilTerakhir = { id = id, path = pathLagu }
  Bridge.simpanTerakhir(id, pathLagu)
  local butir = {}
  for i, item in ipairs(daftar) do
    local status = item.tersedia and "tersedia" or "tidak ditemukan"
    butir[i] = item.nama .. ", " .. status
  end
  butir[#butir + 1] = "Hapus tujuh file hasil"
  layarDaftar("Enam stem dan instrumental", butir, function(i)
    if i <= #daftar then
      local item = daftar[i]
      if not item.tersedia then
        toast("Berkas " .. item.nama .. " tidak ditemukan")
        return
      end
      dialogPesan(item.nama, "Lokasi berkas:\n" .. item.path, {
        { "Putar", function() putar(item.path, item.nama) end },
        { "Salin lokasi", function() salinLokasi(item.path) end },
        { "Kembali", function() tampilHasil(id, pathLagu) end }
      }, function() tampilHasil(id, pathLagu) end)
    else
      dialogPesan("Hapus hasil", "Hapus keenam stem dan file instrumental untuk lagu ini?", {
        { "Hapus", function()
          Bridge.hapusHasil(id)
          hasilTerakhir = nil
          toast("Hasil pemisahan dihapus")
          menuUtama()
        end },
        { "Batal", function() tampilHasil(id, pathLagu) end }
      }, function() tampilHasil(id, pathLagu) end)
    end
  end, function() menuUtama() end,
  T("Pilih salah satu stem untuk diputar atau menyalin lokasinya.", 14))
end

salinLokasi = function(path)
  local ok = pcall(function()
    local clipboard = ctx.getSystemService("clipboard")
    local ClipData = luajava.bindClass("android.content.ClipData")
    clipboard.setPrimaryClip(ClipData.newPlainText("Lokasi audio", path))
  end)
  if ok then toast("Lokasi berkas disalin") else toast("Tidak dapat menyalin lokasi berkas") end
end

putar = function(path, nama)
  if pemutar then
    pcall(function() pemutar.stop() end)
    pcall(function() pemutar.release() end)
    pemutar = nil
  end
  local d = dialogBaru("Memutar " .. nama)
  d.setMessage("Menyiapkan audio...")
  d.setButton("Hentikan", function()
    if pemutar then
      pcall(function() pemutar.stop() end)
      pcall(function() pemutar.release() end)
      pemutar = nil
    end
    tutupDialog()
  end)
  bukaDialog(d)
  local ok, mp = pcall(function() return MediaPlayer() end)
  if not ok then
    dialogPesan("Pemutar gagal", tostring(mp), { { "Tutup", function() menuUtama() end } })
    return
  end
  pemutar = mp
  mp.setOnPreparedListener(MediaPlayer.OnPreparedListener({
    onPrepared = function(player)
      pcall(function() player.start() end)
      pcall(function() d.setMessage("Sedang memutar " .. nama) end)
    end
  }))
  mp.setOnCompletionListener(MediaPlayer.OnCompletionListener({
    onCompletion = function(player)
      pcall(function() player.release() end)
      pemutar = nil
      pcall(function() d.setMessage("Pemutaran selesai") end)
    end
  }))
  local berhasil, err = pcall(function()
    mp.setDataSource(path)
    mp.prepareAsync()
  end)
  if not berhasil then
    pcall(function() mp.release() end)
    pemutar = nil
    d.setMessage("Tidak dapat memutar: " .. tostring(err))
  end
end

statusDialogModel = function(pathLagu)
  local siap, pesan = Bridge.modelSiap()
  if siap then
    if pathLagu then mulaiPemisahan(pathLagu) else menuUtama() end
    return
  end
  dialogPesan("Mesin enam stem belum diaktifkan",
    "Bobot HTDemucs 6-stem memiliki batas lisensi yang belum jelas untuk penggunaan umum. Karena itu, unduhan dan pemakaiannya sementara dinonaktifkan. Kode mesin tetap disimpan sebagai prototipe. Kita sedang menilai model alternatif dengan lisensi bobot yang lebih jelas.",
    {
      { "Kembali ke menu", function() menuUtama() end },
      { "Pilih audio lain", function() pilihAudio() end }
    }, function() menuUtama() end)
end

mulaiUnduhModel = function(pathLagu)
  local ok, pesan = Bridge.unduhModel()
  if not ok then
    dialogPesan("Unduhan tidak dimulai", tostring(pesan), {
      { "Tutup", function() menuUtama() end }
    })
    return
  end
  unduhPantau = true
  local d = dialogBaru("Mengunduh model")
  d.setMessage("Menyiapkan unduhan model...")
  d.setCancelable(false)
  d.setButton("Batalkan", function()
    Bridge.batalUnduhModel()
    d.setMessage("Meminta pembatalan unduhan...")
  end)
  bukaDialog(d)
  local teksLama = ""
  local tahapLama = nil
  local function periksa()
    if not unduhPantau then return end
    local s = Bridge.statusModel()
    if s then
      local teks = s.persen .. " persen. " .. tostring(s.tahap) .. ". " .. tostring(s.pesan)
      if s.tahap and s.tahap ~= "" and s.tahap ~= tahapLama then
        tahapLama = s.tahap
        toast(s.tahap)
      end
      if teks ~= teksLama then
        teksLama = teks
        pcall(function() d.setMessage(teks) end)
      end
      if s.state == "selesai" then
        unduhPantau = false
        tutupDialog()
        toast("Model berhasil diunduh dan diverifikasi")
        if pathLagu then mulaiPemisahan(pathLagu) else menuUtama() end
        return
      elseif s.state == "galat" or s.state == "batal" then
        unduhPantau = false
        dialogPesan("Unduhan model " .. s.state, tostring(s.pesan), {
          { "Tutup", function() menuUtama() end }
        }, function() menuUtama() end)
        return
      end
    end
    tunda(500, periksa)
  end
  tunda(250, periksa)
end

mulaiPemisahan = function(path)
  local siap, pesan = Bridge.modelSiap()
  if not siap then
    statusDialogModel(path)
    return
  end
  local id, galat = Bridge.mulaiPemisahan(path)
  if not id then
    dialogPesan("Pemisahan tidak bisa dimulai", tostring(galat), {
      { "Tutup", function() menuUtama() end }
    }, function() menuUtama() end)
    return
  end
  pantau(id, path)
end

pantau = function(id, path)
  pantauId = id
  local d = dialogBaru("Memisahkan audio")
  d.setMessage("Memulai mesin Demucs...")
  d.setCancelable(false)
  d.setButton("Batalkan", function()
    Bridge.batal(id)
    d.setMessage("Permintaan pembatalan dikirim. Menunggu mesin berhenti...")
  end)
  bukaDialog(d)
  local teksLama = ""
  local tahapLama = nil
  local mulai = os.time()
  local function periksa()
    if pantauId ~= id then return end
    local s = Bridge.status(id)
    if s then
      local teks = s.persen .. " persen. " .. tostring(s.tahap) .. ". " .. tostring(s.pesan)
      if s.tahap and s.tahap ~= "" and s.tahap ~= tahapLama then
        tahapLama = s.tahap
        toast(s.tahap)
      end
      if teks ~= teksLama then
        teksLama = teks
        pcall(function() d.setMessage(teks) end)
      end
      if s.state == "selesai" then
        pantauId = nil
        Bridge.simpanTerakhir(id, path)
        Bridge.hapusAktif()
        tutupDialog()
        tampilHasil(id, path)
        return
      elseif s.state == "galat" or s.state == "batal" then
        pantauId = nil
        Bridge.hapusAktif()
        dialogPesan("Pemisahan " .. s.state, tostring(s.pesan), {
          { "Tutup", function() menuUtama() end }
        }, function() menuUtama() end)
        return
      end
    elseif os.time() - mulai > 10 and Bridge.hidup(id) == "0" then
      pantauId = nil
      Bridge.hapusAktif()
      dialogPesan("Pekerjaan tidak berjalan", "Mesin berhenti sebelum membuat status. Coba lagi dan periksa ruang penyimpanan.", {
        { "Tutup", function() menuUtama() end }
      }, function() menuUtama() end)
      return
    end
    tunda(400, periksa)
  end
  tunda(250, periksa)
end

pilihAudio = function(path)
  path = path or "/storage/emulated/0"
  local folder = File(path)
  if not folder.exists() then
    dialogPesan("Folder tidak ditemukan", path, {
      { "Penyimpanan utama", function() pilihAudio("/storage/emulated/0") end },
      { "Kembali", function() menuUtama() end }
    })
    return
  end
  local daftar = folder.listFiles()
  if daftar == nil then
    dialogPesan("Folder tidak dapat dibaca", path .. "\nPeriksa izin akses berkas untuk Jieshuo.", {
      { "Kembali", function() menuUtama() end }
    })
    return
  end

  local entries = {}
  if path ~= "/" and path ~= "/storage/emulated/0" then
    local parent = folder.getParent()
    if parent then entries[#entries + 1] = { label = "Naik satu folder", path = tostring(parent), dir = true, up = true } end
  end
  for i = 0, panjangLarik(daftar) - 1 do
    local f = daftar[i]
    local name = tostring(f.getName())
    if name ~= "." and name ~= ".." and string.sub(name, 1, 1) ~= "." then
      if f.isDirectory() then
        entries[#entries + 1] = { label = "Folder, " .. name, path = tostring(f.getAbsolutePath()), dir = true }
      elseif f.isFile() then
        local ext = string.lower(string.match(name, "%.([^%.]+)$") or "")
        if EKSTENSI[ext] then
          local size = tonumber(f.length()) or 0
          entries[#entries + 1] = {
            label = name .. ", " .. string.format("%.1f MB", size / 1048576),
            path = tostring(f.getAbsolutePath()), dir = false
          }
        end
      end
    end
  end
  table.sort(entries, function(a, b)
    if a.up then return true end
    if b.up then return false end
    if a.dir ~= b.dir then return a.dir end
    return string.lower(a.label) < string.lower(b.label)
  end)
  local labels = {}
  for i, e in ipairs(entries) do labels[i] = e.label end
  local atas = T("Folder saat ini: " .. path, 13)
  layarDaftar("Pilih audio", labels, function(i)
    local e = entries[i]
    if not e then return end
    if e.dir then
      pilihAudio(e.path)
    else
      dialogPesan("Gunakan audio ini?", e.label, {
        { "Pilih", function() statusDialogModel(e.path) end },
        { "Batal", function() pilihAudio(path) end }
      }, function() pilihAudio(path) end)
    end
  end, function() menuUtama() end, atas)
end

local function menuStatus()
  local ok, info = Bridge.statusMesin()
  local siap, model = Bridge.modelSiap()
  local teks = tostring(info) .. "\n\nStatus model: "
    .. (siap and "siap" or tostring(model))
    .. "\n\nUnduhan HTDemucs saat ini dinonaktifkan sampai hak penggunaan bobot dipastikan."
  dialogPesan("Status mesin dan lisensi model", teks, {
    { "Kembali", function() menuUtama() end }
  }, function() menuUtama() end)
end

menuUtama = function()
  local butir = {
    "Pilih audio untuk dipisahkan",
    "Lihat enam stem dan instrumental terakhir",
    "Status mesin dan model",
    "Pengaturan dan informasi",
    "Tutup"
  }
  layarDaftar(NAMA, butir, function(i)
    if i == 1 then
      pilihAudio()
    elseif i == 2 then
      local last = Bridge.terakhir()
      if not last then
        toast("Belum ada hasil pemisahan")
      else
        tampilHasil(last.id, last.path)
      end
    elseif i == 3 then
      menuStatus()
    elseif i == 4 then
      dialogPesan("Tentang Vocal Remover CSR",
        "Prototipe ekstensi CSR untuk pemisahan audio offline. Mesin C++/Java dan antarmuka Lua sedang dikembangkan. Model HTDemucs 6-stem belum diaktifkan karena status lisensi bobot perlu dipastikan. Proses dirancang berjalan di worker agar UI dan pembaca layar tetap responsif.",
        { { "Kembali", function() menuUtama() end } }, function() menuUtama() end)
    else
      tutupDialog()
    end
  end, function() tutupDialog() end)
end

local function mulaiEkstensi()
  if not ctx then return end
  local base = cariBase()
  local path = base and (base .. "bridge/lua/DJLBridge.lua") or nil
  if not path or not File(path).isFile() then
    dialogPesan("Berkas ekstensi tidak lengkap",
      "DJLBridge.lua tidak ditemukan. Pastikan folder bridge/lua ikut dimasukkan ke paket PPK.",
      { { "Tutup", function() tutupDialog() end } })
    return
  end
  local ok, pesan = siapkanBridge()
  if not ok then
    dialogPesan("Mesin belum siap", tostring(pesan), {
      { "Tutup", function() tutupDialog() end }
    })
    return
  end

  local aktif = Bridge.aktif()
  if aktif then
    local s = Bridge.status(aktif.id)
    if s and s.state == "selesai" then
      Bridge.simpanTerakhir(aktif.id, aktif.path)
      Bridge.hapusAktif()
      tampilHasil(aktif.id, aktif.path)
      return
    elseif s and s.state == "jalan" and Bridge.hidup(aktif.id) == "1" then
      pantau(aktif.id, aktif.path)
      return
    elseif s and s.state == "jalan" then
      if Bridge.hidup(aktif.id) == "1" then
        pantau(aktif.id, aktif.path)
        return
      end
      Bridge.bersihkanSementara(aktif.id)
      dialogPesan("Pekerjaan sebelumnya terhenti",
        "Proses pemisahan sebelumnya sudah tidak berjalan. Berkas sementara dan hasil parsial dibersihkan. Silakan mulai lagi.",
        { { "Lanjutkan", function() menuUtama() end } }, function() menuUtama() end)
      return
    elseif s and (s.state == "galat" or s.state == "batal") then
      Bridge.hapusAktif()
    elseif not s then
      if Bridge.hidup(aktif.id) == "1" then
        pantau(aktif.id, aktif.path)
        return
      end
      Bridge.bersihkanSementara(aktif.id)
    end
  end
  menuUtama()
end

mulaiEkstensi()
