local M = {}

local MODEL_URL = "https://huggingface.co/adefossez/HTDemucs-6s/resolve/3c5ee475be622df764938de97e4281a7b07ffa58/5c90dfd2.safetensors"
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
  local induk = tostring(ctx.getCacheDir().getAbsolutePath())
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
    if dex and so then
      return dex, so, namaSo, calon
    end
  end
  return nil, nil, namaSo, calon
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
  local tersimpan = properties.get("vocalremover.kelas")
  if tersimpan ~= nil then
    kelas = tersimpan
    return kelas
  end

  local dex, so, namaSo, calon = sumberMesin()
  if not dex then
    local daftar = {}
    for _, path in ipairs(calon or {}) do daftar[#daftar + 1] = path end
    return nil, "Berkas mesin tidak ditemukan. Letakkan DJLEngine.dex dan "
      .. namaSo .. " di folder ekstensi. Folder yang diperiksa: "
      .. table.concat(daftar, ", ")
  end

  folder = folderCache("vocal_remover_engine")
  pcall(function() File(folder .. "/odex").mkdirs() end)
  local dexTujuan = folder .. "/DJLEngine_" .. stempel(dex) .. ".dex"
  local soTujuan = folder .. "/libdjl_" .. stempel(so) .. ".so"

  if not berkas(dexTujuan) and not salin(dex, dexTujuan) then
    return nil, "Gagal menyalin DJLEngine.dex ke cache internal"
  end
  if not berkas(soTujuan) and not salin(so, soTujuan) then
    return nil, "Gagal menyalin " .. namaSo .. " ke cache internal"
  end

  local ok, hasil = pcall(function()
    local pemuat = luajava.bindClass("dalvik.system.DexClassLoader")(
      dexTujuan, folder .. "/odex", nil, ctx.getClassLoader())
    return pemuat.loadClass("DJLEngine")
  end)
  if not ok or hasil == nil then
    return nil, "Gagal memuat DJLEngine.dex: " .. tostring(hasil)
  end

  local okMuat, pesan = pcall(function()
    return M.panggil(hasil, "muat", 1, { soTujuan })
  end)
  if not okMuat then return nil, "Gagal memuat mesin C++: " .. tostring(pesan) end
  if string.sub(pesan, 1, 2) ~= "OK" then
    return nil, "Mesin C++ tidak bisa dimuat: "
      .. tostring(string.match(pesan, "|(.*)") or pesan)
  end

  kelas = hasil
  properties.put("vocalremover.kelas", kelas)
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
  local kls, pesan = M.siapkan()
  if not kls then return false, pesan end
  local ok, hasil = pcall(function() return M.panggil(kls, "modelSiap", 0, {}) end)
  if not ok then return false, tostring(hasil) end
  if string.sub(hasil, 1, 2) == "OK" then return true, hasil end
  return false, tostring(string.match(hasil, "|(.*)") or hasil)
end

function M.unduhModel()
  local kls, pesan = M.siapkan()
  if not kls then return false, pesan end
  if unduhAktif then return false, "Pengunduhan model sudah berjalan" end
  local siap, status = M.modelSiap()
  if siap then return true, status end

  statusUnduh = kerja .. "/" .. MODEL_STATUS
  unduhAktif = true
  threadUnduh = Thread(Runnable({
    run = function()
      local ok, hasil = pcall(function()
        return M.panggil(kls, "unduhModel", 2, { MODEL_URL, statusUnduh })
      end)
      if not ok then
        local f = io.open(statusUnduh, "wb")
        if f then
          f:write("galat|0|Unduhan model gagal|" .. tostring(hasil) .. "|" .. tostring(os.time() * 1000))
          f:close()
        end
      end
      unduhAktif = false
    end
  }))
  threadUnduh.start()
  return true, "Pengunduhan model dimulai"
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
    id = "vr" .. tostring(os.time()) .. "_" .. tostring(urutan)
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
    local nama = { "vocals", "drums", "bass", "other", "guitar", "piano" }
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
      for i = 0, isi.length - 1 do hapusPohon(isi[i]) end
    end
  end
  file.delete()
end

function M.hapusHasil(id)
  hapusPohon(File(kerja .. "/stem_" .. id))
  File(kerja .. "/hasil_" .. id .. ".json").delete()
  File(kerja .. "/status_" .. id .. ".txt").delete()
  M.hapusAktif()
  return true
end


function M.simpanTerakhir(id, audioPath)
  local f = io.open(kerja .. "/terakhir.txt", "wb")
  if not f then return false end
  f:write(table.concat({ id, audioPath or "" }, "\\t"))
  f:close()
  return true
end

function M.terakhir()
  local isi = berkas(kerja .. "/terakhir.txt")
  if not isi then return nil end
  local id, path = string.match(isi, "^([^\\t]+)\\t([^\\n]*)")
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

return M
