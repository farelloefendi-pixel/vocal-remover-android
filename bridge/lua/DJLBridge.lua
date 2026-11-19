local M = {}

local MODEL_URL = "https://huggingface.co/aaatmy/uvr-mdx-net-onnx/resolve/main/Kim_Vocal_2.onnx"
local MODEL_ENABLED = true
local MODEL_STATUS = "model_status.txt"
local ACTIVE_FILE = "aktif.txt"

local File = luajava.bindClass("java.io.File")
local System = luajava.bindClass("java.lang.System")
local Kelas = luajava.bindClass("java.lang.Class")
local Objek = luajava.bindClass("java.lang.Object")
local Teks = luajava.bindClass("java.lang.String")
local Thread = luajava.bindClass("java.lang.Thread")
local Runnable = luajava.bindClass("java.lang.Runnable")

local ctx
local base
local kelas
local folder
local kerja
local statusUnduh
local threadUnduh
local unduhAktif = false
local urutan = 0

local function panjangLarik(a)
  local n = 0
  local ok = pcall(function() n = a.length end)
  if not ok or type(n) ~= "number" then pcall(function() n = #a end) end
  return n
end

local function berkas(path)
  local f = io.open(path, "rb")
  if not f then return nil end
  local data = f:read("*a")
  f:close()
  return data
end

local function stempel(data)
  local jumlah = 0
  for i = 1, #data, 7 do
    jumlah = (jumlah + string.byte(data, i)) % 65536
  end
  return tostring(#data) .. "_" .. tostring(jumlah)
end

local function salin(data, tujuan)
  pcall(function() File(tujuan).delete() end)
  local f = io.open(tujuan, "wb")
  if not f then return false end
  local ok = pcall(function() f:write(data) end)
  f:close()
  if not ok then
    pcall(function() File(tujuan).delete() end)
    return false
  end
  pcall(function() File(tujuan).setReadOnly() end)
  return true
end

local function folderCache(nama)
  local induk = tostring(ctx.getFilesDir().getAbsolutePath())
  local path = induk .. "/" .. nama
  pcall(function() File(path).mkdirs() end)
  return path
end

local function sumberMesin()
  local calon = {}
  if base and base ~= "" then calon[#calon + 1] = base end
  local akar = string.match(tostring(base or ""), "^(.-)Plugin/")
  if akar then calon[#calon + 1] = akar .. "Peralatan/" end
  calon[#calon + 1] = "/storage/emulated/0/"

  local is64 = true
  pcall(function()
    is64 = luajava.bindClass("android.os.Process").is64Bit()
  end)
  local namaSo = is64 and "libdjl64.so" or "libdjl32.so"

  for _, dir in ipairs(calon) do
    if string.sub(dir, -1) ~= "/" then dir = dir .. "/" end
    local dex = berkas(dir .. "DJLEngine.dex")
    local so = berkas(dir .. namaSo)
    local ort = berkas(dir .. "libonnxruntime.so")
    local ortJni = berkas(dir .. "libonnxruntime4j_jni.so")
    if dex and so and ort and ortJni then
      return dex, so, ort, ortJni, namaSo, calon
    end
  end
  return nil, nil, nil, nil, namaSo, calon
end

function M.panggil(kls, nama, jumlah, argumen)
  local tipe = luajava.newArray(Kelas, jumlah)
  local arg = luajava.newArray(Objek, jumlah)
  for i = 1, jumlah do
    tipe[i - 1] = Teks
    arg[i - 1] = argumen[i]
  end
  local metode = kls.getDeclaredMethod(nama, tipe)
  return tostring(metode.invoke(nil, arg))
end

function M.mulai(ctxBaru, baseBaru)
  ctx = assert(ctxBaru, "Context CSR tidak tersedia")
  base = baseBaru
  if not base or base == "" then
    pcall(function()
      local path = package.searchpath("main", package.path)
      if path then base = string.match(path, "^(.*/)") end
    end)
  end
  kerja = tostring(ctx.getFilesDir().getAbsolutePath()) .. "/vocal_remover_work"
  pcall(function() File(kerja).mkdirs() end)
  statusUnduh = kerja .. "/" .. MODEL_STATUS
  folder = nil
  kelas = nil
  return true
end

function M.siapkan()
  if not ctx then return nil, "Context CSR belum diinisialisasi" end
  local properties = System.getProperties()
  local cacheKey = "vocalremover.kimmdx.ort131.v4"
  local tersimpan = properties.get(cacheKey)
  if tersimpan ~= nil and tostring(tersimpan) ~= "LOADING" then
    kelas = tersimpan
    return kelas
  end

  if tersimpan == nil then
    local okKlaim, sebelumnya = pcall(function()
      return properties.putIfAbsent(cacheKey, "LOADING")
    end)
    if not okKlaim then
      return nil, "Tidak dapat mengunci pemuatan mesin: " .. tostring(sebelumnya)
    end
    if sebelumnya ~= nil then
      if tostring(sebelumnya) ~= "LOADING" then
        kelas = sebelumnya
        return kelas
      end
      for i = 1, 200 do
        Thread.sleep(100)
        local sekarang = properties.get(cacheKey)
        if sekarang ~= nil and tostring(sekarang) ~= "LOADING" then
          kelas = sekarang
          return kelas
        end
        if sekarang == nil then break end
      end
      return nil, "Pemuatan mesin lain belum selesai. Silakan coba kembali."
    end
  else
    for i = 1, 200 do
      Thread.sleep(100)
      local sekarang = properties.get(cacheKey)
      if sekarang ~= nil and tostring(sekarang) ~= "LOADING" then
        kelas = sekarang
        return kelas
      end
      if sekarang == nil then break end
    end
    return nil, "Pemuatan mesin lain belum selesai. Silakan coba kembali."
  end

  local sukses, hasil, pesan = pcall(function()
    local dex, so, ort, ortJni, namaSo, calon = sumberMesin()
    if not dex then
      local daftar = {}
      for _, path in ipairs(calon or {}) do daftar[#daftar + 1] = path end
      return nil, "Berkas mesin tidak lengkap. Letakkan DJLEngine.dex, "
        .. namaSo .. ", libonnxruntime.so, dan libonnxruntime4j_jni.so di folder ekstensi. Folder yang diperiksa: "
        .. table.concat(daftar, ", ")
    end

    folder = folderCache("vocal_remover_engine")
    pcall(function() File(folder .. "/odex").mkdirs() end)
    local dexTujuan = folder .. "/DJLEngine_" .. stempel(dex) .. ".dex"
    local soTujuan = folder .. "/libdjl_" .. stempel(so) .. ".so"
    local ortFolder = folder .. "/ort_" .. stempel(ort .. ortJni)
    pcall(function() File(ortFolder).mkdirs() end)
    local ortTujuan = ortFolder .. "/libonnxruntime.so"
    local ortJniTujuan = ortFolder .. "/libonnxruntime4j_jni.so"

    if not berkas(dexTujuan) and not salin(dex, dexTujuan) then
      return nil, "Gagal menyalin DJLEngine.dex ke cache internal"
    end
    if not berkas(soTujuan) and not salin(so, soTujuan) then
      return nil, "Gagal menyalin " .. namaSo .. " ke cache internal"
    end
    if not berkas(ortTujuan) and not salin(ort, ortTujuan) then
      return nil, "Gagal menyalin libonnxruntime.so ke cache internal"
    end
    if not berkas(ortJniTujuan) and not salin(ortJni, ortJniTujuan) then
      return nil, "Gagal menyalin libonnxruntime4j_jni.so ke cache internal"
    end

    local pemuat = luajava.bindClass("dalvik.system.DexClassLoader")(
      dexTujuan, folder .. "/odex", ortFolder, ctx.getClassLoader())
    local hasilKelas = pemuat.loadClass("DJLEngine")
    local hasilMuat = M.panggil(hasilKelas, "muat", 2, { soTujuan, ortFolder })
    if string.sub(hasilMuat, 1, 2) ~= "OK" then
      return nil, "Mesin C++ tidak bisa dimuat: "
        .. tostring(string.match(hasilMuat, "|(.*)") or hasilMuat)
    end
    return hasilKelas, hasilMuat
  end)

  if not sukses or hasil == nil then
    pcall(function() properties.remove(cacheKey) end)
    return nil, tostring(pesan or hasil)
  end

  kelas = hasil
  properties.put(cacheKey, kelas)
  return kelas, pesan
end

function M.lokasiModel()
  local kls, pesan = M.siapkan()
  if not kls then return nil, pesan end
  local ok, path = pcall(function() return M.panggil(kls, "lokasiModel", 0, {}) end)
  if not ok then return nil, tostring(path) end
  return path
end

function M.modelSiap()
  if not MODEL_ENABLED then
    return false, "Mesin Kim Vocal 2 sedang dinonaktifkan oleh konfigurasi."
  end
  local kls, pesan = M.siapkan()
  if not kls then return false, pesan end
  local ok, hasil = pcall(function() return M.panggil(kls, "modelSiap", 0, {}) end)
  if not ok then return false, tostring(hasil) end
  if string.sub(hasil, 1, 2) == "OK" then return true, hasil end
  return false, tostring(string.match(hasil, "|(.*)") or hasil)
end

function M.unduhModel()
  if not MODEL_ENABLED or not MODEL_URL then
    return false, "Unduhan model dinonaktifkan oleh konfigurasi."
  end
  if unduhAktif then return false, "Pengunduhan model sudah berjalan" end

  statusUnduh = kerja .. "/" .. MODEL_STATUS
  unduhAktif = true
  local awal = io.open(statusUnduh, "wb")
  if awal then
    awal:write("jalan|0|Menyiapkan mesin|Menyiapkan komponen untuk mengunduh model|" .. tostring(os.time() * 1000))
    awal:close()
  end

  threadUnduh = Thread(Runnable({
    run = function()
      pcall(function()
        luajava.bindClass("android.os.Process").setThreadPriority(10)
      end)
      local ok, hasil = pcall(function()
        local kls, pesan = M.siapkan()
        if not kls then return "GALAT|" .. tostring(pesan) end
        local siap, status = M.modelSiap()
        if siap then return "OK|Model sudah tersedia dan terverifikasi" end
        return M.panggil(kls, "unduhModel", 2, { MODEL_URL, statusUnduh })
      end)
      local hasilTeks = tostring(hasil or "")
      if not ok or string.sub(hasilTeks, 1, 5) == "GALAT" then
        local f = io.open(statusUnduh, "wb")
        if f then
          f:write("galat|0|Unduhan model gagal|" .. hasilTeks .. "|" .. tostring(os.time() * 1000))
          f:close()
        end
      elseif string.sub(hasilTeks, 1, 2) == "OK" then
        local f = io.open(statusUnduh, "wb")
        if f then
          f:write("selesai|100|Model siap|" .. tostring(string.match(hasilTeks, "|(.*)") or hasilTeks)
            .. "|" .. tostring(os.time() * 1000))
          f:close()
        end
      end
      unduhAktif = false
    end
  }))
  threadUnduh.start()
  return true, "Persiapan dan pengunduhan model berjalan di latar belakang"
end

function M.batalUnduhModel()
  if not unduhAktif or not kelas then return false end
  pcall(function() M.panggil(kelas, "batalUnduhModel", 0, {}) end)
  return true
end

function M.statusModel()
  if not statusUnduh then return nil end
  local isi = berkas(statusUnduh)
  if not isi then
    if unduhAktif then
      return { state = "jalan", persen = 0, tahap = "Menghubungkan", pesan = "Menyiapkan unduhan" }
    end
    return nil
  end
  local state, persen, tahap, pesan, waktu =
    string.match(isi, "^([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^\n]*)")
  if not state then return nil end
  return {
    state = state,
    persen = tonumber(persen) or 0,
    tahap = tahap,
    pesan = pesan,
    waktu = tonumber(waktu) or 0
  }
end

function M.pasangModel(path)
  if not MODEL_ENABLED then
    return false, "Pemasangan model dinonaktifkan oleh konfigurasi."
  end
  local kls, pesan = M.siapkan()
  if not kls then return false, pesan end
  local ok, hasil = pcall(function()
    return M.panggil(kls, "pasangModel", 1, { path })
  end)
  if not ok then return false, tostring(hasil) end
  return string.sub(hasil, 1, 2) == "OK", hasil
end

function M.status(id)
  local isi = berkas(kerja .. "/status_" .. id .. ".txt")
  if not isi then return nil end
  local state, persen, tahap, pesan, waktu =
    string.match(isi, "^([^|]*)|([^|]*)|([^|]*)|([^|]*)|([^\n]*)")
  if not state then return nil end
  return {
    state = state,
    persen = tonumber(persen) or 0,
    tahap = tahap,
    pesan = pesan,
    waktu = tonumber(waktu) or 0
  }
end

function M.hidup(id)
  local kls = M.siapkan()
  if not kls then return "0" end
  local ok, hasil = pcall(function() return M.panggil(kls, "hidup", 1, { id }) end)
  return ok and hasil or "0"
end

function M.mulaiPemisahan(audioPath, id)
  local kls, pesan = M.siapkan()
  if not kls then return nil, pesan end
  local siap, sebab = M.modelSiap()
  if not siap then return nil, "Model belum siap: " .. tostring(sebab) end
  if not audioPath or audioPath == "" or not File(audioPath).isFile() then
    return nil, "Berkas audio tidak ditemukan"
  end
  if not id or id == "" then
    urutan = urutan + 1
    local SystemJ = luajava.bindClass("java.lang.System")
    id = "vr" .. tostring(SystemJ.currentTimeMillis()) .. "_" .. tostring(SystemJ.nanoTime()) .. "_" .. tostring(urutan)
  end
  local ok, hasil = pcall(function()
    return M.panggil(kls, "mulai", 3, { audioPath, kerja, id })
  end)
  if not ok then return nil, tostring(hasil) end
  if string.sub(hasil, 1, 2) ~= "OK" then
    return nil, tostring(string.match(hasil, "|(.*)") or hasil)
  end
  local f = io.open(kerja .. "/" .. ACTIVE_FILE, "wb")
  if f then
    f:write(table.concat({ id, audioPath, tostring(os.time() * 1000) }, "\t"))
    f:close()
  end
  return id
end

function M.batal(id)
  local kls = M.siapkan()
  if not kls then return false end
  local ok = pcall(function()
    M.panggil(kls, "batal", 2, { kerja, id })
  end)
  return ok
end

function M.ambilHasil(id)
  local isi = berkas(kerja .. "/hasil_" .. id .. ".json")
  if not isi then return nil, "Berkas hasil belum tersedia" end
  local ok, hasil = pcall(function()
    local json = luajava.bindClass("org.json.JSONObject")(isi)
    local outputs = json.getJSONObject("outputs")
    local nama = { "vocals", "instrumental" }
    local daftar = {}
    for _, k in ipairs(nama) do
      local path = outputs.getString(k)
      daftar[#daftar + 1] = { id = k, nama = k, path = path, tersedia = File(path).isFile() }
    end
    return daftar
  end)
  if not ok then return nil, "Hasil tidak bisa dibaca: " .. tostring(hasil) end
  return hasil
end

function M.aktif()
  local isi = berkas(kerja .. "/" .. ACTIVE_FILE)
  if not isi then return nil end
  local id, path, waktu = string.match(isi, "^([^\t]+)\t([^\t]+)\t([^\n]+)")
  if not id then return nil end
  return { id = id, path = path, waktu = tonumber(waktu) or 0 }
end

function M.hapusAktif()
  pcall(function() File(kerja .. "/" .. ACTIVE_FILE).delete() end)
end

local function hapusPohon(file)
  if not file.exists() then return end
  if file.isDirectory() then
    local isi = file.listFiles()
    if isi ~= nil then
      for i = 0, panjangLarik(isi) - 1 do hapusPohon(isi[i]) end
    end
  end
  file.delete()
end

function M.hapusHasil(id)
  hapusPohon(File(kerja .. "/stem_" .. id))
  File(kerja .. "/hasil_" .. id .. ".json").delete()
  File(kerja .. "/status_" .. id .. ".txt").delete()
  local last = M.terakhir()
  if last and last.id == id then File(kerja .. "/terakhir.txt").delete() end
  local active = M.aktif()
  if active and active.id == id then M.hapusAktif() end
  return true
end

function M.bersihkanSementara(id)
  File(kerja .. "/audio_" .. id .. ".f32").delete()
  File(kerja .. "/siap_" .. id .. ".f32").delete()
  File(kerja .. "/batal_" .. id .. ".flag").delete()
  hapusPohon(File(kerja .. "/stem_" .. id))
  File(kerja .. "/hasil_" .. id .. ".json").delete()
  File(kerja .. "/status_" .. id .. ".txt").delete()
  M.hapusAktif()
  return true
end


function M.simpanTerakhir(id, audioPath)
  local f = io.open(kerja .. "/terakhir.txt", "wb")
  if not f then return false end
  f:write(table.concat({ id, audioPath or "" }, "\t"))
  f:close()
  return true
end

function M.terakhir()
  local isi = berkas(kerja .. "/terakhir.txt")
  if not isi then return nil end
  local id, path = string.match(isi, "^([^\t]+)\t([^\n]*)")
  if not id then return nil end
  return { id = id, path = path }
end

function M.folderKerja()
  return kerja
end

function M.statusMesin()
  local kls, pesan = M.siapkan()
  if not kls then return false, pesan end
  local info = M.panggil(kls, "infoMesin", 0, {})
  return true, tostring(pesan or "Mesin siap.") .. " " .. tostring(info)
end

function M.ujiModelKimVocal2(path)
  local kls, pesan = M.siapkan()
  if not kls then return false, pesan end
  local ok, hasil = pcall(function()
    return M.panggil(kls, "ujiModelKimVocal2", 1, { path })
  end)
  if not ok then return false, tostring(hasil) end
  return string.sub(hasil, 1, 2) == "OK", hasil
end

return M
