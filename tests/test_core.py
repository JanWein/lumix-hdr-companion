from pathlib import Path
from dataclasses import replace
import json
import cv2
import numpy as np
import pytest
from PIL import Image
from lumix_hdr.core import Frame, group_frames, merge, read_frame, valid_group
from lumix_hdr.cli import scan
from scripts.make_demo import make_demo


@pytest.fixture
def bracket(tmp_path):
    folder = tmp_path / "input"
    make_demo(folder)
    return folder, sorted(folder.glob("*.jpg"))


def test_group_metadata_and_missing_frame(bracket):
    folder, paths = bracket
    frames = [read_frame(p) for p in paths]
    assert len(group_frames(frames)) == 1
    assert group_frames(frames[:2]) == []
    assert group_frames([replace(x, bias=0) for x in frames]) == []
    assert group_frames([frames[0], replace(frames[1], iso=200), frames[2]]) == []
    assert group_frames([frames[0], replace(frames[1], timestamp=frames[1].timestamp+30), frames[2]]) == []


def test_complete_then_incomplete_series(bracket):
    _, paths = bracket
    frames = [read_frame(p) for p in paths]
    other = [replace(f, path=Path("second_"+f.path.name), timestamp=f.timestamp+10) for f in frames]
    groups = group_frames(frames + other[:2])
    assert len(groups) == 1


def test_real_radiance_export_and_reconstruction(bracket, tmp_path):
    folder, paths = bracket
    result = merge(paths, tmp_path / "out", align=False)
    radiance = cv2.imread(result["radiance_hdr"], cv2.IMREAD_UNCHANGED)
    assert radiance.dtype == np.float32
    assert np.isfinite(radiance).all() and radiance.min() >= 0
    assert np.percentile(radiance, 99) / np.percentile(radiance, 5) > 8
    truth = np.load(folder / "ground_truth.npy")[:, :, ::-1]
    # Evaluate only recoverable pixels; absolute scale is arbitrary after response calibration.
    mask = (truth > .07) & (truth < 1.8)
    ratio = np.median(radiance[mask] / truth[mask])
    relative_error = np.median(np.abs(radiance[mask] / ratio - truth[mask]) / truth[mask])
    assert relative_error < .15
    image = cv2.imread(result["jpeg"])
    assert image.std() > 20 and image.shape == (384, 576, 3)


def test_fusion_and_no_radiance_claim(bracket, tmp_path):
    _, paths = bracket
    result = merge(paths, tmp_path / "fusion", mode="fusion", align=False)
    assert result["radiance_hdr"] is None
    assert cv2.imread(result["jpeg"]).std() > 10


def test_watch_cache_separates_settings_and_recovers_deleted_output(bracket, tmp_path):
    _, paths = bracket
    folder = tmp_path / "outputs"
    hdr = merge(paths, folder, mode="hdr", align=False)
    fusion = merge(paths, folder, mode="fusion", align=False)
    before = Path(fusion["jpeg"]).stat().st_mtime_ns
    again = merge(paths, folder, mode="fusion", align=False)
    assert again["jpeg"] == fusion["jpeg"]
    assert Path(again["jpeg"]).stat().st_mtime_ns == before
    Path(hdr["jpeg"]).unlink()
    assert Path(merge(paths, folder, mode="hdr", align=False)["jpeg"]).is_file()


def test_alignment_and_auto_scan(bracket, tmp_path):
    folder, paths = bracket
    result = scan(folder, tmp_path / "out", stable_seconds=0)
    assert len(result) == 1
    assert result[0]["width"] >= 460
    first_mtime = Path(result[0]["jpeg"]).stat().st_mtime_ns
    result2 = scan(folder, tmp_path / "out", stable_seconds=0)
    assert Path(result2[0]["jpeg"]).stat().st_mtime_ns == first_mtime


def test_missing_exif_and_duplicate_inputs(tmp_path):
    paths=[]
    for i in range(3):
        p=tmp_path / f"{i}.jpg"
        Image.fromarray(np.full((160,200,3),50+i*50,dtype=np.uint8)).save(p)
        paths.append(p)
    with pytest.raises(ValueError, match="EXIF"):
        merge(paths,tmp_path/"out",align=False)
    with pytest.raises(ValueError, match="distinct"):
        merge([paths[0]]*3,tmp_path/"out",mode="fusion")


def test_incorrect_dimensions(bracket,tmp_path):
    _,paths=bracket
    p=tmp_path/"small.jpg";Image.new("RGB",(40,40)).save(p)
    with pytest.raises(ValueError,match="dimensions"):
        merge([paths[0],paths[1],p],tmp_path/"out",mode="fusion")
