import sharp from 'sharp'
import { readdir, stat } from 'node:fs/promises'
import { join, extname, basename } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(fileURLToPath(import.meta.url), '../../src/assets')
const PUBLIC = join(fileURLToPath(import.meta.url), '../../public')

const JPEG_QUALITY = 80
const PNG_QUALITY = 80
const WEBP_QUALITY = 80
const MAX_WIDTH = 2000

async function getFiles(dir, exts = ['.jpg', '.jpeg', '.png']) {
  const entries = await readdir(dir, { withFileTypes: true })
  const files = []
  for (const e of entries) {
    const full = join(dir, e.name)
    if (e.isFile() && exts.includes(extname(e.name).toLowerCase())) {
      files.push(full)
    }
  }
  return files
}

async function compressImage(filePath) {
  const ext = extname(filePath).toLowerCase()
  const base = basename(filePath)
  const { size: originalSize } = await stat(filePath)

  let pipeline = sharp(filePath)

  // PNG → WebP (better compression for web, preserves transparency)
  if (ext === '.png') {
    const outputPath = filePath.replace(/\.png$/i, '.webp')
    pipeline = pipeline.resize({ width: MAX_WIDTH, withoutEnlargement: true })

    // Check if image has transparency
    const metadata = await sharp(filePath).metadata()
    if (metadata.hasAlpha) {
      await pipeline.webp({ quality: WEBP_QUALITY, alphaQuality: 90 }).toFile(outputPath)
    } else {
      // No transparency, convert to JPEG for even smaller size
      const jpgPath = filePath.replace(/\.png$/i, '.jpg')
      await pipeline.jpeg({ quality: JPEG_QUALITY, mozjpeg: true }).toFile(jpgPath)
      const { size: newSize } = await stat(jpgPath)
      console.log(`  ${base}: ${formatSize(originalSize)} → ${formatSize(newSize)} (PNG→JPG, ${percent(originalSize, newSize)})`)
      return
    }

    const { size: newSize } = await stat(outputPath)
    console.log(`  ${base}: ${formatSize(originalSize)} → ${formatSize(newSize)} (PNG→WebP, ${percent(originalSize, newSize)})`)
    return
  }

  // JPEG → compressed JPEG
  const outputPath = filePath
  pipeline = pipeline
    .resize({ width: MAX_WIDTH, withoutEnlargement: true })
    .jpeg({ quality: JPEG_QUALITY, mozjpeg: true })

  // Write to temp then replace
  const tmpPath = filePath + '.tmp'
  await pipeline.toFile(tmpPath)

  const { size: newSize } = await stat(tmpPath)

  // Only replace if actually smaller
  if (newSize < originalSize) {
    const { rename } = await import('node:fs/promises')
    await rename(tmpPath, outputPath)
    console.log(`  ${base}: ${formatSize(originalSize)} → ${formatSize(newSize)} (${percent(originalSize, newSize)})`)
  } else {
    const { unlink } = await import('node:fs/promises')
    await unlink(tmpPath)
    console.log(`  ${base}: ${formatSize(originalSize)} (已是最优)`)
  }
}

function formatSize(bytes) {
  return bytes >= 1024 * 1024
    ? `${(bytes / (1024 * 1024)).toFixed(1)}MB`
    : `${(bytes / 1024).toFixed(0)}KB`
}

function percent(orig, now) {
  const pct = ((1 - now / orig) * 100).toFixed(0)
  return `减小 ${pct}%`
}

async function main() {
  console.log('📦 压缩 src/assets/ 图片...\n')
  const assets = await getFiles(ROOT)
  for (const f of assets) {
    await compressImage(f)
  }

  console.log('\n📦 压缩 public/ 图片...\n')
  const pubFiles = await getFiles(PUBLIC)
  for (const f of pubFiles) {
    await compressImage(f)
  }

  console.log('\n✅ 图片压缩完成')
}

main().catch(console.error)
