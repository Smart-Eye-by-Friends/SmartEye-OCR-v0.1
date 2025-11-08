from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from shutil import copy2
from threading import Lock
from typing import Dict, Iterable, Optional

import torch
from huggingface_hub import hf_hub_download
from loguru import logger


try:
    from doclayout_yolo import YOLOv10
except ImportError as exc:  # pragma: no cover - 환경 의존
    YOLOv10 = None  # type: ignore[assignment]
    _IMPORT_ERROR = exc
else:
    _IMPORT_ERROR = None


@dataclass(frozen=True)
class ModelSpec:
    name: str
    repo_id: str
    filename: str
    imgsz: int = 1024
    conf: float = 0.25


@dataclass
class ModelHandle:
    name: str
    spec: ModelSpec
    model: "YOLOv10"
    device: str
    weight_path: Path


class ModelRegistry:
    """
    DocLayout-YOLO 계열 모델을 전역으로 캐싱/재사용하기 위한 레지스트리.
    - 모델별 가중치 다운로드는 한 번만 수행
    - 디바이스(CPU/GPU)별 인스턴스를 필요 시 별도로 유지
    """

    def __init__(self) -> None:
        self._specs: Dict[str, ModelSpec] = {}
        self._models: Dict[str, ModelHandle] = {}
        self._locks: Dict[str, Lock] = {}
        self._default_device = "cuda" if torch.cuda.is_available() else "cpu"

    @staticmethod
    def _make_key(name: str, device: str) -> str:
        return f"{name}:{device}"

    def register(self, spec: ModelSpec) -> None:
        self._specs[spec.name] = spec
        self._locks.setdefault(spec.name, Lock())
        logger.debug(f"📘 모델 스펙 등록: {spec.name} (imgsz={spec.imgsz}, conf={spec.conf})")

    def list_registered(self) -> Dict[str, ModelSpec]:
        return dict(self._specs)

    def preload(self, targets: Optional[Iterable[str]] = None, *, device: Optional[str] = None) -> None:
        names = list(targets) if targets else list(self._specs.keys())
        for name in names:
            try:
                self.get_model(name, device=device)
            except Exception as exc:  # pragma: no cover - 초기화 단계
                logger.error(f"❌ 모델 프리로드 실패 ({name}): {exc}")
                raise

    def get_model(self, name: str, *, device: Optional[str] = None) -> ModelHandle:
        if name not in self._specs:
            raise KeyError(f"등록되지 않은 모델입니다: {name}")

        if _IMPORT_ERROR is not None:
            raise RuntimeError(
                "doclayout_yolo 패키지가 설치되지 않아 모델을 로드할 수 없습니다."
            ) from _IMPORT_ERROR

        resolved_device = device or self._default_device
        key = self._make_key(name, resolved_device)

        if key in self._models:
            return self._models[key]

        lock = self._locks.setdefault(name, Lock())
        with lock:
            if key in self._models:
                return self._models[key]

            spec = self._specs[name]
            weight_path = self._download_weights(name, spec)
            model = self._load_model(weight_path, resolved_device)

            handle = ModelHandle(
                name=name,
                spec=spec,
                model=model,
                device=resolved_device,
                weight_path=weight_path,
            )
            self._models[key] = handle
            logger.info(f"✅ 모델 로드 완료: {name} (device={resolved_device})")
            return handle

    @staticmethod
    def _download_weights(name: str, spec: ModelSpec) -> Path:
        override_env = os.getenv(f"{name.upper()}_MODEL_PATH")
        if override_env:
            override_path = Path(override_env)
            if override_path.exists():
                logger.info(f"📂 {name} 가중치 경로 override 사용: {override_path}")
                return override_path.resolve()
            logger.warning(
                f"⚠️ {name.upper()}_MODEL_PATH 가 지정되었지만 파일을 찾을 수 없습니다: {override_path}"
            )

        cache_root = Path(
            os.getenv("MODEL_CACHE_DIR", Path.home() / ".cache" / "smarteye_models")
        ).resolve()
        target_dir = (cache_root / name).resolve()
        target_dir.mkdir(parents=True, exist_ok=True)
        target_path = target_dir / spec.filename

        if target_path.exists():
            logger.debug(f"📦 캐시된 가중치 사용: {target_path}")
            return target_path

        logger.info(f"⬇️ {name} 가중치 다운로드 중 ({spec.repo_id}/{spec.filename})")
        downloaded_path = hf_hub_download(
            repo_id=spec.repo_id,
            filename=spec.filename,
            local_dir=str(target_dir),
            local_dir_use_symlinks=False,
        )

        downloaded_path = Path(downloaded_path).resolve()
        if downloaded_path != target_path:
            copy2(downloaded_path, target_path)
            logger.debug(f"📁 가중치 복사: {downloaded_path.name} -> {target_path}")

        return target_path

    @staticmethod
    def _load_model(weight_path: Path, device: str) -> "YOLOv10":
        if YOLOv10 is None:  # pragma: no cover
            raise RuntimeError("doclayout_yolo 패키지가 없습니다.")

        logger.info(f"🧠 모델 로딩: {weight_path.name} (device={device})")
        model = YOLOv10(str(weight_path), task="predict")
        model.to(device)
        if hasattr(model, "training"):
            model.training = False
        return model


# ---------------------------------------------------------------------------
# 전역 레지스트리 인스턴스 및 기본 모델 스펙 등록
# ---------------------------------------------------------------------------
DEFAULT_MODEL_SPECS = [
    ModelSpec(
        name="SmartEyeSsen",
        repo_id="AkJeond/SmartEye",
        filename="best.pt",
        imgsz=1024,
        conf=0.25,
    ),
    ModelSpec(
        name="docstructbench",
        repo_id="juliozhao/DocLayout-YOLO-DocStructBench",
        filename="doclayout_yolo_docstructbench_imgsz1024.pt",
        imgsz=1024,
        conf=0.25,
    )
]

model_registry = ModelRegistry()
for spec in DEFAULT_MODEL_SPECS:
    model_registry.register(spec)
