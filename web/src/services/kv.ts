/**
 * Plan-Zwischenspeicher in IndexedDB (die Pläne sind zu groß für localStorage). Scheitert der Zugriff
 * (privater Modus, Speicher gesperrt), merkt sich die App die Pläne nur im Arbeitsspeicher.
 */
const DB = 'nextlesson';
const STORE = 'plaene';
const FOTOS = 'fotos';

export interface CacheEintrag {
  xml: string;
  zeit: number;
}

const speicher = new Map<string, CacheEintrag>();
let dbVersprechen: Promise<IDBDatabase | null> | null = null;

function oeffnen(): Promise<IDBDatabase | null> {
  if (!dbVersprechen) {
    dbVersprechen = new Promise((fertig) => {
      try {
        const anfrage = indexedDB.open(DB, 2);
        anfrage.onupgradeneeded = () => {
          const db = anfrage.result;
          if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE);
          if (!db.objectStoreNames.contains(FOTOS)) db.createObjectStore(FOTOS);
        };
        anfrage.onsuccess = () => fertig(anfrage.result);
        anfrage.onerror = () => fertig(null);
        anfrage.onblocked = () => fertig(null);
      } catch {
        fertig(null);
      }
    });
  }
  return dbVersprechen;
}

function anfrage<T>(modus: IDBTransactionMode, f: (s: IDBObjectStore) => IDBRequest<T>, store = STORE): Promise<T | null> {
  return oeffnen().then(
    (db) =>
      new Promise<T | null>((fertig) => {
        if (!db) return fertig(null);
        try {
          const r = f(db.transaction(store, modus).objectStore(store));
          r.onsuccess = () => fertig(r.result);
          r.onerror = () => fertig(null);
        } catch {
          fertig(null);
        }
      })
  );
}

export async function cacheLesen(key: string): Promise<CacheEintrag | null> {
  const e = (await anfrage<CacheEintrag | undefined>('readonly', (s) => s.get(key))) ?? speicher.get(key) ?? null;
  return e ?? null;
}

export async function cacheSchreiben(key: string, eintrag: CacheEintrag): Promise<void> {
  speicher.set(key, eintrag);
  await anfrage('readwrite', (s) => s.put(eintrag, key));
}

/** Entfernt Pläne, die älter als [maxAlterMs] sind. */
export async function cacheAufraeumen(maxAlterMs: number): Promise<void> {
  const grenze = Date.now() - maxAlterMs;
  const keys = (await anfrage<IDBValidKey[]>('readonly', (s) => s.getAllKeys())) ?? [...speicher.keys()];
  for (const k of keys) {
    const e = await cacheLesen(String(k));
    if (e && e.zeit < grenze) {
      speicher.delete(String(k));
      await anfrage('readwrite', (s) => s.delete(k));
    }
  }
}

// ---------- Fotos zu Hausaufgaben (z. B. das Tafelbild) ----------

const fotosSpeicher = new Map<string, Blob>();

/** Verkleinert ein Bild auf höchstens 1600 Pixel Kantenlänge und macht ein JPEG daraus. */
export async function fotoVerkleinern(datei: Blob): Promise<Blob | null> {
  try {
    const bmp = await createImageBitmap(datei);
    const f = Math.min(1, 1600 / Math.max(bmp.width, bmp.height));
    const cv = document.createElement('canvas');
    cv.width = Math.max(1, Math.round(bmp.width * f));
    cv.height = Math.max(1, Math.round(bmp.height * f));
    cv.getContext('2d')!.drawImage(bmp, 0, 0, cv.width, cv.height);
    return await new Promise((ok) => cv.toBlob((b) => ok(b), 'image/jpeg', 0.85));
  } catch {
    return null;
  }
}

export async function fotoSpeichern(id: string, bild: Blob): Promise<void> {
  fotosSpeicher.set(id, bild);
  await anfrage('readwrite', (s) => s.put(bild, id), FOTOS);
}

export async function fotoLesen(id: string): Promise<Blob | null> {
  const b = await anfrage<Blob | undefined>('readonly', (s) => s.get(id), FOTOS);
  return b ?? fotosSpeicher.get(id) ?? null;
}

export async function fotoLoeschen(id: string): Promise<void> {
  fotosSpeicher.delete(id);
  await anfrage('readwrite', (s) => s.delete(id), FOTOS);
}

/** Ids aller gespeicherten Fotos. */
export async function fotoIds(): Promise<string[]> {
  const keys = await anfrage<IDBValidKey[]>('readonly', (s) => s.getAllKeys(), FOTOS);
  return (keys ?? [...fotosSpeicher.keys()]).map(String);
}

/** Entfernt Fotos, zu denen es keine Hausaufgabe mehr gibt. */
export async function fotosAufraeumen(behalten: string[]): Promise<void> {
  const ok = new Set(behalten);
  for (const id of await fotoIds()) if (!ok.has(id)) await fotoLoeschen(id);
}
