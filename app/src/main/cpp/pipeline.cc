#include "pipeline.h"
#include "utils.h"
#include <algorithm>
#include <cmath>
#include <fstream>
#include <sstream>

namespace {

cv::Mat GetRotateCropImage(cv::Mat srcimage, std::vector<std::vector<int>> box) {
  cv::Mat image;
  srcimage.copyTo(image);
  std::vector<std::vector<int>> points = box;

  int xCollect[4] = {box[0][0], box[1][0], box[2][0], box[3][0]};
  int yCollect[4] = {box[0][1], box[1][1], box[2][1], box[3][1]};
  int left = int(*std::min_element(xCollect, xCollect + 4));
  int right = int(*std::max_element(xCollect, xCollect + 4));
  int top = int(*std::min_element(yCollect, yCollect + 4));
  int bottom = int(*std::max_element(yCollect, yCollect + 4));

  left = std::max(left, 0);
  top = std::max(top, 0);
  right = std::min(right, image.cols);
  bottom = std::min(bottom, image.rows);
  if (right <= left || bottom <= top) {
    return cv::Mat();
  }

  cv::Mat imgCrop;
  image(cv::Rect(left, top, right - left, bottom - top)).copyTo(imgCrop);

  for (int i = 0; i < points.size(); i++) {
    points[i][0] -= left;
    points[i][1] -= top;
  }

  int imgCropWidth = static_cast<int>(sqrt(pow(points[0][0] - points[1][0], 2) +
                                           pow(points[0][1] - points[1][1], 2)));
  int imgCropHeight = static_cast<int>(sqrt(pow(points[0][0] - points[3][0], 2) +
                                            pow(points[0][1] - points[3][1], 2)));
  if (imgCropWidth <= 0 || imgCropHeight <= 0) {
    return cv::Mat();
  }

  cv::Point2f ptsStd[4];
  ptsStd[0] = cv::Point2f(0.f, 0.f);
  ptsStd[1] = cv::Point2f(imgCropWidth, 0.f);
  ptsStd[2] = cv::Point2f(imgCropWidth, imgCropHeight);
  ptsStd[3] = cv::Point2f(0.f, imgCropHeight);

  cv::Point2f pointsf[4];
  pointsf[0] = cv::Point2f(points[0][0], points[0][1]);
  pointsf[1] = cv::Point2f(points[1][0], points[1][1]);
  pointsf[2] = cv::Point2f(points[2][0], points[2][1]);
  pointsf[3] = cv::Point2f(points[3][0], points[3][1]);

  cv::Mat transform = cv::getPerspectiveTransform(pointsf, ptsStd);
  cv::Mat dstImg;
  cv::warpPerspective(imgCrop, dstImg, transform,
                      cv::Size(imgCropWidth, imgCropHeight),
                      cv::BORDER_REPLICATE);

  const float ratio = 1.5f;
  if (static_cast<float>(dstImg.rows) >= static_cast<float>(dstImg.cols) * ratio) {
    cv::Mat rotated;
    cv::transpose(dstImg, rotated);
    cv::flip(rotated, rotated, 0);
    return rotated;
  }
  return dstImg;
}

std::vector<std::string> ReadDict(const std::string &path) {
  std::ifstream in(path);
  std::string line;
  std::vector<std::string> values;
  while (getline(in, line)) {
    values.push_back(line);
  }
  return values;
}

std::map<std::string, double> LoadConfigTxt(const std::string &configPath) {
  auto lines = ReadDict(configPath);
  std::map<std::string, double> config;
  for (const auto &line : lines) {
    std::istringstream stream(line);
    std::string key;
    double value;
    if (stream >> key >> value) {
      config[key] = value;
    }
  }
  return config;
}

} // namespace

Pipeline::Pipeline(const std::string &detModelDir,
                   const std::string &clsModelDir,
                   const std::string &recModelDir,
                   const std::string &cpuPowerMode, int cpuThreadNum,
                   const std::string &configPath,
                   const std::string &dictPath) {
  clsPredictor_.reset(new ClsPredictor(clsModelDir, cpuThreadNum, cpuPowerMode));
  detPredictor_.reset(new DetPredictor(detModelDir, cpuThreadNum, cpuPowerMode));
  recPredictor_.reset(new RecPredictor(recModelDir, cpuThreadNum, cpuPowerMode));
  config_ = LoadConfigTxt(configPath);
  characterDict_ = ReadDict(dictPath);
  characterDict_.insert(characterDict_.begin(), "#");
  characterDict_.push_back(" ");
}

std::string Pipeline::Recognize(const cv::Mat &bgrImage) {
  if (bgrImage.empty()) {
    return "";
  }

  cv::Mat srcImg;
  bgrImage.copyTo(srcImg);
  auto boxes = detPredictor_->Predict(srcImg, config_, nullptr, nullptr, nullptr);
  int useDirectionClassify = int(config_["use_direction_classify"]);

  std::vector<std::string> lines;
  for (int i = boxes.size() - 1; i >= 0; i--) {
    cv::Mat cropImg = GetRotateCropImage(srcImg, boxes[i]);
    if (cropImg.empty()) {
      continue;
    }
    if (useDirectionClassify >= 1) {
      cropImg = clsPredictor_->Predict(cropImg, nullptr, nullptr, nullptr, 0.9);
    }
    auto result = recPredictor_->Predict(cropImg, nullptr, nullptr, nullptr, characterDict_);
    if (!result.first.empty() && result.second >= 0.25f) {
      lines.push_back(result.first);
    }
  }

  std::ostringstream output;
  for (size_t i = 0; i < lines.size(); i++) {
    if (i > 0) output << "\n";
    output << lines[i];
  }
  return output.str();
}

