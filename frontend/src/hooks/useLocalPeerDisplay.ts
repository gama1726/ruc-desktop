import { useCallback, useMemo, useState } from "react";

const ID_KEY = "ruc-local-display-id";
const PW_KEY = "ruc-local-access-key";

function randomDigits(n: number): string {
  let s = "";
  for (let i = 0; i < n; i++) {
    s += String(Math.floor(Math.random() * 10));
  }
  return s;
}

function randomAccessKey(): string {
  const chars = "abcdefghjkmnpqrstuvwxyz23456789";
  let s = "";
  for (let i = 0; i < 6; i++) {
    s += chars[Math.floor(Math.random() * chars.length)];
  }
  return s;
}

function formatId(raw: string): string {
  const d = raw.replace(/\D/g, "").slice(0, 9);
  const a = d.slice(0, 3);
  const b = d.slice(3, 6);
  const c = d.slice(6, 9);
  return [a, b, c].filter(Boolean).join(" ");
}

function ensureStoredId(): string {
  let raw = localStorage.getItem(ID_KEY);
  if (!raw || raw.replace(/\D/g, "").length !== 9) {
    raw = randomDigits(9);
    localStorage.setItem(ID_KEY, raw);
  }
  return raw;
}

function ensureStoredPassword(): string {
  let p = localStorage.getItem(PW_KEY);
  if (!p || p.length < 4) {
    p = randomAccessKey();
    localStorage.setItem(PW_KEY, p);
  }
  return p;
}

export function useLocalPeerDisplay() {
  const [idRaw] = useState(() => ensureStoredId());
  const [password, setPassword] = useState(() => ensureStoredPassword());

  const displayId = useMemo(() => formatId(idRaw), [idRaw]);

  const rotatePassword = useCallback(() => {
    const next = randomAccessKey();
    localStorage.setItem(PW_KEY, next);
    setPassword(next);
  }, []);

  const copyId = useCallback(() => {
    void navigator.clipboard.writeText(idRaw.replace(/\D/g, ""));
  }, [idRaw]);

  return { displayId, idDigits: idRaw.replace(/\D/g, ""), password, rotatePassword, copyId };
}
