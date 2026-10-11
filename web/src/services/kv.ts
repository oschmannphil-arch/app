/**
 * Plan-Zwischenspeicher in IndexedDB (die Pläne sind zu groß für localStorage). Scheitert der Zugriff
 * (privater Modus, Speicher gesperrt), merkt sich die App die Pläne nur im Arbeitsspeicher.
 */
const DB = 'nextlesson';
const STORE = 'plaene';

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
        const anfrage = indexedDB.open(DB, 1);
        anfrage.onupgradeneeded = () => anfrage.result.createObjectStore(STORE);
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

function anfrage<T>(modus: IDBTransactionMode, f: (s: IDBObjectStore) => IDBRequest<T>): Promise<T | null> {
  return oeffnen().then(
    (db) =>
      new Promise<T | null>((fertig) => {
        if (!db) return fertig(null);
        try {
          const r = f(db.transaction(STORE, modus).objectStore(STORE));
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
